-- Habits.
--
-- No habit tables. That is the point of migration 0009: a feature's rows live in
-- `entity_state` as jsonb, keyed by (user, entity, entity_id), and the client writes
-- them through the generic sync platform. Adding four `habit_*` tables here would give
-- the app two sources of truth for the same habit — the phone's synced projection and a
-- REST-only mirror — and they would drift the first time one write path failed.
--
-- What this migration does add is the two things the generic platform genuinely cannot
-- infer: how a habit row merges when two devices touched it, and the indexes the habits
-- read models need to answer "what happened in June" without scanning a user's whole
-- state.

-- ---------------------------------------------------------------------------
-- Conflict policy, per entity.
-- ---------------------------------------------------------------------------
INSERT INTO sync_policy (entity, strategy) VALUES
    -- A habit is edited by several screens at once: Create/Edit rewrites name, icon and
    -- goal while the home list rewrites sortIndex and the reminder sheet rewrites
    -- reminderTime. Whole-object LWW would silently revert whichever landed first.
    ('habit',            'merge'),

    -- One row per (habit, day). Deliberately LWW and not 'merge': the newest device
    -- clock is the answer the user last gave, whether that was complete, skip or undo.
    -- Merging would let a stale `skipReason` survive a later completion.
    ('habitEntry',       'lww'),

    -- Notes are written once and rarely edited.
    ('habitNote',        'lww'),

    -- 'max' is what makes an achievement monotonic: `unlockedAt` and `progress` are both
    -- numeric, so an out-of-order replay from a device that had not yet earned the badge
    -- can never lock it again or walk its progress backwards.
    ('habitAchievement', 'max')
ON CONFLICT (entity) DO UPDATE SET strategy = EXCLUDED.strategy, updated_at = now();

-- ---------------------------------------------------------------------------
-- Read-model indexes.
--
-- Keyed on `entity`, deliberately NOT on `feature`. The device labels a synced table
-- with the table's own name unless a local syncPolicy row overrides it, so the same
-- habit row can legitimately arrive labelled "habit" from an older build and "habits"
-- from a newer one. `entity` is the table name and never varies, which makes it the
-- only safe predicate — and it is already unique across the app, so nothing is lost.
--
-- These cover the two queries that filter INSIDE the payload, which the existing
-- `entity_state_feature` index cannot help with.
-- ---------------------------------------------------------------------------

-- Calendar, statistics, dashboard: entries within a date window.
CREATE INDEX entity_state_habit_entry_date
    ON entity_state (user_id, (payload ->> 'dateKey'))
    WHERE entity = 'habitEntry' AND deleted = false;

-- Habit detail: one habit's entries and notes.
CREATE INDEX entity_state_habit_child
    ON entity_state (user_id, entity, (payload ->> 'habitId'))
    WHERE entity IN ('habitEntry', 'habitNote') AND deleted = false;
