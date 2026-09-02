DROP INDEX IF EXISTS entity_state_habit_child;
DROP INDEX IF EXISTS entity_state_habit_entry_date;

DELETE FROM sync_policy
 WHERE entity IN ('habit', 'habitEntry', 'habitNote', 'habitAchievement');
