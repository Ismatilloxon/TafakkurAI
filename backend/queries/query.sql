-- Representative sqlc queries. The hand-written pgx repositories mirror these;
-- extend this file and run `make sqlc` to grow the typed-query layer.

-- name: GetUserByProviderSubject :one
SELECT * FROM users WHERE provider = $1 AND provider_subject = $2;

-- name: GetProfile :one
SELECT * FROM profiles WHERE user_id = $1;

-- name: ListSubjects :many
SELECT * FROM subjects ORDER BY order_index, title;

-- name: ListLessonsBySubject :many
SELECT * FROM lessons WHERE subject_id = $1 ORDER BY order_index, title;

-- name: FeedPage :many
SELECT t.id, t.user_id, t.text, t.like_count, t.created_at
FROM thoughts t
ORDER BY t.created_at DESC, t.id DESC
LIMIT $1;

-- name: GetGamification :one
SELECT * FROM gamification WHERE user_id = $1;
