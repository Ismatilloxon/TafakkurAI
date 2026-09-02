package com.example.tafakkurai.sync

import app.cash.sqldelight.db.SqlDriver
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive

/** Lenient on purpose: a payload written by a newer app version must not break an older one. */
internal val syncJson: Json = Json {
    ignoreUnknownKeys = true
    isLenient = true
    encodeDefaults = true
    explicitNulls = true
}

/**
 * Turns rows into JSON and JSON back into rows, for any table, without knowing what
 * the table is.
 *
 * Columns come from `pragma_table_info` at runtime, so this file never needs editing
 * when a feature adds a column — let alone a table.
 *
 * ### Why the JSON is built by SQLite and not by Kotlin
 *
 * Building it in Kotlin would mean reading every column through a typed cursor, and
 * SQLDelight's `SqlCursor` exposes no column metadata, so the platform would need
 * per-table code — the exact thing it exists to avoid. Letting SQLite concatenate the
 * object keeps the read generic and costs one query per event at flush time.
 *
 * `json_object()` would be the obvious tool and is not available: Android's bundled
 * SQLite only gained JSON1 at API 30 and this app ships to API 29. The escape chain
 * below is the portable equivalent.
 */
internal object SyncPayload {

    /**
     * A SQL expression rendering one column as a JSON value.
     *
     * The four `replace` calls cover every character JSON forbids raw in a string that
     * can realistically come from a text field or a serialized list: backslash, quote,
     * newline, carriage return, tab. Backslash must be first, or the escapes added
     * afterwards would be escaped again.
     *
     * Blobs become hex strings. No table in the app stores one today, but a future
     * feature caching an image would otherwise produce a payload that is not valid JSON
     * and fails silently at the far end.
     */
    private fun jsonValue(column: String): String {
        val q = "\"$column\""
        val escaped = listOf(
            "'\\'" to "'\\\\'",   // \  ->  \\
            "'\"'" to "'\\\"'",   // "  ->  \"
            "char(10)" to "'\\n'",
            "char(13)" to "'\\r'",
            "char(9)" to "'\\t'",
        ).fold(q) { acc, (from, to) -> "replace($acc, $from, $to)" }

        return """
            CASE
                WHEN $q IS NULL THEN 'null'
                WHEN typeof($q) IN ('integer', 'real') THEN CAST($q AS TEXT)
                WHEN typeof($q) = 'blob' THEN '"' || hex($q) || '"'
                ELSE '"' || $escaped || '"'
            END
        """.trimIndent()
    }

    /**
     * Reads the current state of one row as a JSON object.
     *
     * Returns null when the row is gone — which is normal and not an error: a row can be
     * deleted between the change that queued the event and the flush that sends it. The
     * caller turns that into a delete rather than shipping a payload for a row that no
     * longer exists.
     */
    fun materialize(driver: SqlDriver, table: SyncSchema.Table, entityId: String): String? {
        val body = table.columns.joinToString(" || ',' || ") { c ->
            "'\"${c.name}\":' || " + jsonValue(c.name)
        }
        val where = whereClause(table)
        val values = entityId.split(ENTITY_ID_SEPARATOR)
        if (values.size != table.primaryKey.size) return null

        val rows = driver.executeQuery(
            null,
            "SELECT '{' || $body || '}' FROM \"${table.name}\" WHERE $where",
            { cursor ->
                val out = ArrayList<String?>()
                while (cursor.next().value) out.add(cursor.getString(0))
                app.cash.sqldelight.db.QueryResult.Value(out)
            },
            values.size,
        ) {
            values.forEachIndexed { i, v -> bindString(i, v) }
        }.value

        return rows.firstOrNull()
    }

    /**
     * Writes a row that came from the server into its local table.
     *
     * Only keys that are real columns are written, so a payload produced by a newer app
     * version (with a column this build does not have) applies its known fields instead
     * of failing. The reverse — a column this build has and the payload lacks — falls
     * back to the column default, which is why every added column needs a default.
     *
     * The caller MUST have capture disabled around this. Writing here with triggers live
     * would queue an event for a change that came from the server, push it back, and
     * ping-pong forever.
     */
    fun applyUpsert(driver: SqlDriver, table: SyncSchema.Table, payload: String) {
        val obj = runCatching { syncJson.parseToJsonElement(payload) as? JsonObject }.getOrNull() ?: return
        val byName = table.columns.associateBy { it.name }
        val cols = obj.keys.mapNotNull { byName[it] }
        if (cols.isEmpty()) return

        val names = cols.joinToString(", ") { "\"${it.name}\"" }
        val holes = cols.joinToString(", ") { "?" }

        driver.execute(
            null,
            "INSERT OR REPLACE INTO \"${table.name}\" ($names) VALUES ($holes)",
            cols.size,
        ) {
            cols.forEachIndexed { i, c -> bindColumn(i, c, obj[c.name]) }
        }.value
    }

    /** Removes a row the server says is gone. Same capture rule as [applyUpsert]. */
    fun applyDelete(driver: SqlDriver, table: SyncSchema.Table, entityId: String) {
        val values = entityId.split(ENTITY_ID_SEPARATOR)
        if (values.size != table.primaryKey.size) return
        driver.execute(
            null,
            "DELETE FROM \"${table.name}\" WHERE ${whereClause(table)}",
            values.size,
        ) {
            values.forEachIndexed { i, v -> bindString(i, v) }
        }.value
    }

    /**
     * Binds one JSON value using the column's declared affinity rather than the JSON
     * type. SQLite would coerce most mismatches silently, but binding a REAL column as
     * text makes later `ORDER BY` and range queries compare as strings — the classic
     * "10 sorts before 9" bug, and one that would only show up in whichever feature
     * happened to query that column.
     */
    private fun app.cash.sqldelight.db.SqlPreparedStatement.bindColumn(
        index: Int,
        column: SyncSchema.Column,
        value: kotlinx.serialization.json.JsonElement?,
    ) {
        val primitive = value as? JsonPrimitive
        if (primitive == null || primitive.isNull()) {
            bindString(index, null)
            return
        }
        val text = primitive.content
        when {
            column.declaredType.contains("INT") -> bindLong(index, text.toLongOrNull())
            column.declaredType.contains("REAL") ||
                column.declaredType.contains("FLOA") ||
                column.declaredType.contains("DOUB") -> bindDouble(index, text.toDoubleOrNull())
            column.declaredType.contains("BLOB") -> bindBytes(index, text.hexToBytesOrNull())
            else -> bindString(index, text)
        }
    }

    private fun whereClause(table: SyncSchema.Table): String =
        table.primaryKey.joinToString(" AND ") { "\"${it.name}\" = ?" }
}

/**
 * Separator between primary-key parts inside `entityId`. ASCII unit separator, because
 * it cannot occur in a date key, a prayer name, a surah number or any other identifier
 * the app uses — a printable separator like `|` eventually collides with real data and
 * splits one row's identity into two.
 */
internal const val ENTITY_ID_SEPARATOR = '\u001F'

private fun JsonPrimitive.isNull(): Boolean = this is kotlinx.serialization.json.JsonNull

private fun String.hexToBytesOrNull(): ByteArray? {
    if (length % 2 != 0) return null
    return runCatching {
        ByteArray(length / 2) { i -> substring(i * 2, i * 2 + 2).toInt(16).toByte() }
    }.getOrNull()
}

/** Builds the metadata blob attached to every event. Kept tiny — it travels per row. */
internal fun syncMetadata(appVersion: String, platform: String): String =
    syncJson.encodeToString(
        JsonObject.serializer(),
        buildJsonObject {
            put("app", JsonPrimitive(appVersion))
            put("platform", JsonPrimitive(platform))
        },
    )

/** Reads a string field out of a payload without deserializing into a typed model. */
internal fun String.jsonField(name: String): String? =
    runCatching {
        (syncJson.parseToJsonElement(this) as? JsonObject)?.get(name)?.jsonPrimitive?.content
    }.getOrNull()
