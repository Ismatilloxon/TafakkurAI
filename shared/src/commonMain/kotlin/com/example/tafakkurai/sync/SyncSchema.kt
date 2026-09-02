package com.example.tafakkurai.sync

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver

/**
 * The part of the platform that makes "never implement sync again" literally true.
 *
 * On every start it asks SQLite what tables exist, works out each one's primary key,
 * and installs three triggers on it. A feature that adds `MeditationLog.sq` tomorrow
 * and writes to it through its own repository is synchronised on the next launch,
 * having touched nothing in this package.
 *
 * ### Why triggers rather than a Kotlin abstraction
 *
 * A base `SyncedRepository` or a wrapped DAO would still require every feature to opt
 * in by extending or wrapping it, and would miss any write that went around it. The
 * database sees every write by definition, and Kotlin/Native has no reflection to
 * discover repositories with at runtime, so a trigger is the only mechanism that can
 * be both automatic and complete.
 *
 * ### Why they are reinstalled every start
 *
 * Trigger bodies bake in the feature label and the primary key, so a policy change or
 * a schema migration would leave a stale trigger behind. Dropping and recreating the
 * whole set costs a few dozen DDL statements once per launch — far cheaper than
 * reasoning about drift.
 */
internal object SyncSchema {

    /** Engine bookkeeping and SQLite implementation tables, never user-owned content. */
    private val PLATFORM_TABLES = setOf(
        "syncEvent", "syncSession", "syncCursor", "syncPolicy", "syncMetric",
        "android_metadata", "sqlite_sequence", "room_master_table",
    )

    /**
     * Tables the platform owns or that are meaningless to another device. Everything
     * else syncs by default: opt-out, never opt-in, because an opt-in list is exactly
     * the "register your feature" step this design exists to remove.
     */
    private val INTERNAL_TABLES = PLATFORM_TABLES + setOf(
        // ActivityLog writes this durable local copy and one inline outbox event in the
        // same transaction. Capturing the table again would upload every action twice.
        "activityEvent",
        // Private device cache; the canonical profile record contains only its object URL.
        "profilePhoto",
        // Lawfully supplied book/source text stays on-device. Resulting flashcards sync,
        // but copying the original source into a generic cloud outbox would violate privacy.
        "vocabularySource",
    )

    /** Millis since the Unix epoch, in SQL. `strftime('%s')` would lose the sub-second part. */
    private const val NOW_MILLIS = "CAST((julianday('now') - 2440587.5) * 86400000 AS INTEGER)"

    /**
     * A v4 UUID assembled from randomblob. The server rejects anything that is not a
     * UUID, because the event id doubles as its idempotency key and a colliding id
     * would silently drop somebody's change.
     */
    private const val NEW_UUID = """
        lower(hex(randomblob(4))) || '-' || lower(hex(randomblob(2))) || '-4' ||
        substr(lower(hex(randomblob(2))), 2) || '-' ||
        substr('89ab', abs(random()) % 4 + 1, 1) ||
        substr(lower(hex(randomblob(2))), 2) || '-' || lower(hex(randomblob(6)))
    """

    data class Column(val name: String, val declaredType: String, val pkOrder: Int)

    data class Table(val name: String, val columns: List<Column>) {
        /** Primary-key columns in declaration order. Empty means the table cannot sync. */
        val primaryKey: List<Column> = columns.filter { it.pkOrder > 0 }.sortedBy { it.pkOrder }
    }

    /** One table's sync settings. Absent from `syncPolicy` means enabled, feature = table. */
    data class Policy(val feature: String, val enabled: Boolean)

    // ------------------------------------------------------------------ discovery

    fun discover(driver: SqlDriver): List<Table> =
        driver.rawQuery("SELECT name FROM sqlite_master WHERE type = 'table'", 1)
            .mapNotNull { it[0] }
            .filter { it !in INTERNAL_TABLES && !it.startsWith("sqlite_") }
            .map { name -> Table(name, columnsOf(driver, name)) }

    /**
     * Every local table that can contain account data, including intentionally unsynced
     * private caches such as profile photos and imported vocabulary sources, and the activity
     * ledger that syncs through its explicit append-only path rather than capture triggers.
     * Used only for an explicit shared-device sign-out wipe.
     */
    fun localAccountTableNames(driver: SqlDriver): List<String> =
        driver.rawQuery("SELECT name FROM sqlite_master WHERE type = 'table'", 1)
            .mapNotNull { it[0] }
            .filter { it !in PLATFORM_TABLES && !it.startsWith("sqlite_") }

    private fun columnsOf(driver: SqlDriver, table: String): List<Column> =
        driver.rawQuery(
            "SELECT name, type, CAST(pk AS TEXT) FROM pragma_table_info(${table.sqlLiteral()})", 3,
        ).map { row ->
            Column(
                name = row[0].orEmpty(),
                declaredType = row[1].orEmpty().uppercase(),
                pkOrder = row[2]?.toIntOrNull() ?: 0,
            )
        }

    // ------------------------------------------------------------------ triggers

    /**
     * Drops every trigger this object owns and reinstalls one set per syncable table.
     *
     * Returns the tables that are actually being captured, so the caller can log what
     * is (and is not) synchronised without this object owning a logger.
     */
    fun installTriggers(
        driver: SqlDriver,
        tables: List<Table>,
        policy: (String) -> Policy,
        onSkipped: (table: String, reason: String) -> Unit = { _, _ -> },
    ): List<Table> {
        driver.rawQuery(
            "SELECT name FROM sqlite_master WHERE type = 'trigger' AND name LIKE 'sync\$_%' ESCAPE '\$'", 1,
        ).mapNotNull { it[0] }.forEach { driver.exec("DROP TRIGGER IF EXISTS \"$it\"") }

        val captured = mutableListOf<Table>()
        for (table in tables) {
            val p = policy(table.name)
            if (!p.enabled) {
                onSkipped(table.name, "disabled in syncPolicy")
                continue
            }
            if (table.primaryKey.isEmpty()) {
                // Without a declared primary key the only identity SQLite offers is the
                // rowid, which is assigned per device and means nothing on another one.
                // Syncing such a row would create duplicates rather than merge them.
                onSkipped(table.name, "no PRIMARY KEY — cannot be identified across devices")
                continue
            }
            driver.exec(insertTrigger(table, p.feature))
            driver.exec(updateTrigger(table, p.feature))
            driver.exec(deleteTrigger(table, p.feature))
            captured += table
        }
        return captured
    }

    private fun insertTrigger(t: Table, feature: String) = """
        CREATE TRIGGER IF NOT EXISTS "sync_ai_${t.name}"
        AFTER INSERT ON "${t.name}"
        WHEN (SELECT capture FROM syncSession WHERE id = 0) = 1
        BEGIN
            ${emit(t, feature, "NEW", "insert")}
        END
    """.trimIndent()

    private fun updateTrigger(t: Table, feature: String) = """
        CREATE TRIGGER IF NOT EXISTS "sync_au_${t.name}"
        AFTER UPDATE ON "${t.name}"
        WHEN (SELECT capture FROM syncSession WHERE id = 0) = 1
        BEGIN
            ${emit(t, feature, "NEW", "update")}
        END
    """.trimIndent()

    private fun deleteTrigger(t: Table, feature: String) = """
        CREATE TRIGGER IF NOT EXISTS "sync_ad_${t.name}"
        AFTER DELETE ON "${t.name}"
        WHEN (SELECT capture FROM syncSession WHERE id = 0) = 1
        BEGIN
            ${emit(t, feature, "OLD", "delete")}
        END
    """.trimIndent()

    /**
     * The statement every trigger runs. Two things are worth reading closely:
     *
     * **The upsert is the coalescing.** It targets the partial unique index on
     * `(dedupeKey) WHERE status='pending'`, so a second change to the same row while
     * the first is still queued overwrites it instead of adding a row. That is how a
     * thousand dhikr taps become one event, and it costs nothing: SQLite does it
     * inside the write the user already triggered.
     *
     * **A delete always wins the merge.** If a row is created and deleted before the
     * queue drains, the surviving event is the delete — the server upserts a tombstone
     * and every device converges on "gone". Keeping the insert instead would resurrect
     * it on the next pull.
     *
     * Note the payload is left NULL. Materialising the row here would need `json_object`,
     * which Android's bundled SQLite does not have below API 30 (minSdk is 29). It is
     * built instead at flush time by [SyncPayload], from columns discovered at runtime.
     */
    private fun emit(t: Table, feature: String, ref: String, op: String): String {
        val id = t.primaryKey.joinToString(" || char(31) || ") {
            "COALESCE(CAST($ref.\"${it.name}\" AS TEXT), '')"
        }
        return """
            INSERT INTO syncEvent (
                id, userId, feature, entity, entityId, operation, dedupeKey,
                payload, metadata, deviceTime, timezone, status, retryCount, createdAt, updatedAt
            ) VALUES (
                $NEW_UUID,
                (SELECT userId FROM syncSession WHERE id = 0),
                ${feature.sqlLiteral()},
                ${t.name.sqlLiteral()},
                $id,
                '$op',
                (SELECT userId FROM syncSession WHERE id = 0) || char(31) || ${t.name.sqlLiteral()} || char(31) || $id,
                NULL, NULL, $NOW_MILLIS,
                (SELECT timezone FROM syncSession WHERE id = 0),
                'pending', 0, $NOW_MILLIS, $NOW_MILLIS
            )
            ON CONFLICT(dedupeKey) WHERE status = 'pending' DO UPDATE SET
                operation  = CASE
                                WHEN excluded.operation = 'delete' THEN 'delete'
                                WHEN syncEvent.operation = 'insert' THEN 'insert'
                                ELSE excluded.operation
                             END,
                deviceTime = excluded.deviceTime,
                updatedAt  = excluded.updatedAt;
        """.trimIndent()
    }

    // ------------------------------------------------------------------ backfill

    /**
     * Emits one event per existing row of [table] for [userId].
     *
     * Needed twice: when this version first runs on a device that already has data, and
     * when a user signs in on a device whose local database predates their account. The
     * triggers only see writes that happen after they exist, so without this the rows a
     * user already had would never reach the server.
     *
     * `INSERT OR IGNORE` makes it idempotent against the partial unique index: a row
     * that already has a pending event is left alone rather than duplicated.
     */
    fun backfill(driver: SqlDriver, table: Table, feature: String, userId: String) {
        if (table.primaryKey.isEmpty()) return
        val id = table.primaryKey.joinToString(" || char(31) || ") {
            "COALESCE(CAST(t.\"${it.name}\" AS TEXT), '')"
        }
        driver.exec(
            """
            INSERT OR IGNORE INTO syncEvent (
                id, userId, feature, entity, entityId, operation, dedupeKey,
                payload, metadata, deviceTime, timezone, status, retryCount, createdAt, updatedAt
            )
            SELECT
                $NEW_UUID,
                ${userId.sqlLiteral()},
                ${feature.sqlLiteral()},
                ${table.name.sqlLiteral()},
                $id,
                'insert',
                ${userId.sqlLiteral()} || char(31) || ${table.name.sqlLiteral()} || char(31) || $id,
                NULL, NULL, $NOW_MILLIS,
                (SELECT timezone FROM syncSession WHERE id = 0),
                'pending', 0, $NOW_MILLIS, $NOW_MILLIS
            FROM "${table.name}" t
            """.trimIndent(),
        )
    }
}

// ---------------------------------------------------------------------------- raw SQL

/**
 * Escapes a Kotlin string into a SQL string literal.
 *
 * Every value that reaches this comes from `sqlite_master` or `pragma_table_info` —
 * i.e. from names the app itself declared in .sq files, never from user input — but
 * trigger bodies are DDL and cannot be parameterised, so the doubling is not optional.
 */
internal fun String.sqlLiteral(): String = "'" + replace("'", "''") + "'"

/** Fire-and-forget DDL/DML on the raw driver, forcing the (synchronous) result. */
internal fun SqlDriver.exec(sql: String) {
    execute(null, sql, 0).value
}

/**
 * Runs [sql] and returns every row as a list of nullable strings.
 *
 * Reading everything as text is deliberate: this is only used for schema introspection
 * and for payload assembly, where SQLite has already produced the JSON, so a typed
 * cursor would buy nothing and would need per-column knowledge the platform refuses
 * to have.
 */
internal fun SqlDriver.rawQuery(sql: String, columnCount: Int): List<List<String?>> =
    executeQuery(null, sql, { cursor ->
        val rows = ArrayList<List<String?>>()
        while (cursor.next().value) {
            rows.add((0 until columnCount).map { cursor.getString(it) })
        }
        QueryResult.Value(rows)
    }, 0).value
