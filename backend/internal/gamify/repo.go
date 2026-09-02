package gamify

import (
	"context"
	"errors"
	"fmt"
	"time"

	"github.com/google/uuid"
	"github.com/jackc/pgx/v5"
	"github.com/jackc/pgx/v5/pgxpool"
)

var ErrNotFound = errors.New("gamification row not found")

type Repo struct {
	pool *pgxpool.Pool
}

type awardBatchResult struct {
	Awards []AwardResult
	State  State
}

type activityCalendar struct {
	days   map[int64]struct{}
	latest int64
	hasDay bool
}

func NewRepo(pool *pgxpool.Pool) *Repo { return &Repo{pool: pool} }

func (r *Repo) EnsureExists(ctx context.Context, userID uuid.UUID) error {
	s := NewState()
	_, err := r.pool.Exec(ctx, `
		INSERT INTO gamification (user_id, streak, level, xp, xp_next, last_active_date)
		VALUES ($1, $2, $3, $4, $5, NULL)
		ON CONFLICT (user_id) DO NOTHING`,
		userID, s.Streak, s.Level, s.XP, s.XPNext)
	if err != nil {
		return fmt.Errorf("ensure gamification state: %w", err)
	}
	return nil
}

func (r *Repo) Get(ctx context.Context, userID uuid.UUID) (State, error) {
	var s State
	var last *time.Time
	err := r.pool.QueryRow(ctx, `
		SELECT streak, level, xp, xp_next, last_active_date
		FROM gamification WHERE user_id = $1`, userID,
	).Scan(&s.Streak, &s.Level, &s.XP, &s.XPNext, &last)
	if errors.Is(err, pgx.ErrNoRows) {
		return State{}, ErrNotFound
	}
	if err != nil {
		return State{}, fmt.Errorf("get gamification state: %w", err)
	}
	if last != nil {
		s.LastActiveDate = civilDate(*last)
	}
	return s, nil
}

// awardBatch serializes progression updates on the user's gamification row.
// The row lock prevents two different commands from reading the same XP value,
// while the award ledger makes a retry return the first command's result.
func (r *Repo) awardBatch(
	ctx context.Context,
	userID uuid.UUID,
	commands []AwardCommand,
) (awardBatchResult, error) {
	tx, err := r.pool.BeginTx(ctx, pgx.TxOptions{})
	if err != nil {
		return awardBatchResult{}, fmt.Errorf("begin gamification award: %w", err)
	}
	defer func() { _ = tx.Rollback(ctx) }()
	result, err := r.awardBatchTx(ctx, tx, userID, commands)
	if err != nil {
		return awardBatchResult{}, err
	}
	if err := tx.Commit(ctx); err != nil {
		return awardBatchResult{}, fmt.Errorf("commit gamification award: %w", err)
	}
	return result, nil
}

// awardBatchTx applies progression using a caller-owned transaction. Sync uses this
// path so its immutable activity append, entity-state projection, award ledger and
// aggregate progression either all commit or all roll back.
func (r *Repo) awardBatchTx(
	ctx context.Context,
	tx pgx.Tx,
	userID uuid.UUID,
	commands []AwardCommand,
) (awardBatchResult, error) {
	if err := ensureExistsTx(ctx, tx, userID); err != nil {
		return awardBatchResult{}, err
	}
	current, err := stateForUpdate(ctx, tx, userID)
	if err != nil {
		return awardBatchResult{}, err
	}
	calendar, err := loadActivityCalendar(ctx, tx, userID)
	if err != nil {
		return awardBatchResult{}, err
	}

	result := awardBatchResult{
		Awards: make([]AwardResult, 0, len(commands)),
		State:  current,
	}
	changed := false
	for _, command := range commands {
		previous, found, err := awardResult(ctx, tx, userID, command)
		if err != nil {
			return awardBatchResult{}, err
		}
		if found {
			result.Awards = append(result.Awards, previous)
			continue
		}

		if err := insertActivityDay(ctx, tx, userID, command.LocalDate); err != nil {
			return awardBatchResult{}, err
		}
		calendar.add(command.LocalDate)
		next := Apply(current, command.Event, command.LocalDate)
		next.Streak, next.LastActiveDate = calendar.activeStreak()
		if err := insertAward(ctx, tx, userID, command, next); err != nil {
			return awardBatchResult{}, err
		}

		award := AwardResult{State: next, Applied: true}
		result.Awards = append(result.Awards, award)
		current = next
		result.State = next
		changed = true
	}

	if changed {
		if err := saveTx(ctx, tx, userID, current); err != nil {
			return awardBatchResult{}, err
		}
	}
	return result, nil
}

func ensureExistsTx(ctx context.Context, tx pgx.Tx, userID uuid.UUID) error {
	s := NewState()
	_, err := tx.Exec(ctx, `
		INSERT INTO gamification (user_id, streak, level, xp, xp_next, last_active_date)
		VALUES ($1, $2, $3, $4, $5, NULL)
		ON CONFLICT (user_id) DO NOTHING`,
		userID, s.Streak, s.Level, s.XP, s.XPNext)
	if err != nil {
		return fmt.Errorf("ensure gamification state in award: %w", err)
	}
	return nil
}

func stateForUpdate(ctx context.Context, tx pgx.Tx, userID uuid.UUID) (State, error) {
	var s State
	var last *time.Time
	err := tx.QueryRow(ctx, `
		SELECT streak, level, xp, xp_next, last_active_date
		FROM gamification
		WHERE user_id = $1
		FOR UPDATE`, userID,
	).Scan(&s.Streak, &s.Level, &s.XP, &s.XPNext, &last)
	if err != nil {
		return State{}, fmt.Errorf("lock gamification state: %w", err)
	}
	if last != nil {
		s.LastActiveDate = civilDate(*last)
	}
	return s, nil
}

func awardResult(
	ctx context.Context,
	tx pgx.Tx,
	userID uuid.UUID,
	command AwardCommand,
) (AwardResult, bool, error) {
	var state State
	err := tx.QueryRow(ctx, `
		SELECT result_streak, result_level, result_xp, result_xp_next,
		       result_last_active_date
		FROM gamification_awards
		WHERE user_id = $1 AND event = $2 AND idempotency_key = $3`,
		userID, command.Event, command.IdempotencyKey,
	).Scan(
		&state.Streak,
		&state.Level,
		&state.XP,
		&state.XPNext,
		&state.LastActiveDate,
	)
	if errors.Is(err, pgx.ErrNoRows) {
		return AwardResult{}, false, nil
	}
	if err != nil {
		return AwardResult{}, false, fmt.Errorf("get prior gamification award: %w", err)
	}
	// The source identity owns the date of its first successful award. A replay
	// can arrive after midnight; returning the stored result is safer than
	// turning a successful source write into a later 500 or moving its streak day.
	state.LastActiveDate = civilDate(state.LastActiveDate)
	return AwardResult{State: state, Applied: false}, true, nil
}

func insertActivityDay(ctx context.Context, tx pgx.Tx, userID uuid.UUID, localDate time.Time) error {
	_, err := tx.Exec(ctx, `
		INSERT INTO gamification_activity_days (user_id, local_date)
		VALUES ($1, $2)
		ON CONFLICT (user_id, local_date) DO NOTHING`, userID, localDate)
	if err != nil {
		return fmt.Errorf("record gamification activity day: %w", err)
	}
	return nil
}

func loadActivityCalendar(ctx context.Context, tx pgx.Tx, userID uuid.UUID) (*activityCalendar, error) {
	rows, err := tx.Query(ctx, `
		SELECT local_date
		FROM gamification_activity_days
		WHERE user_id = $1`, userID)
	if err != nil {
		return nil, fmt.Errorf("load gamification activity days: %w", err)
	}
	defer rows.Close()

	calendar := &activityCalendar{days: make(map[int64]struct{})}
	for rows.Next() {
		var localDate time.Time
		if err := rows.Scan(&localDate); err != nil {
			return nil, fmt.Errorf("scan gamification activity day: %w", err)
		}
		calendar.add(localDate)
	}
	if err := rows.Err(); err != nil {
		return nil, fmt.Errorf("iterate gamification activity days: %w", err)
	}
	return calendar, nil
}

func (c *activityCalendar) add(localDate time.Time) {
	day := civilDate(localDate).Unix() / int64((24*time.Hour)/time.Second)
	c.days[day] = struct{}{}
	if !c.hasDay || day > c.latest {
		c.latest = day
		c.hasDay = true
	}
}

func (c *activityCalendar) activeStreak() (int, time.Time) {
	if !c.hasDay {
		return 0, time.Time{}
	}
	streak := 0
	for day := c.latest; ; day-- {
		if _, ok := c.days[day]; !ok {
			break
		}
		streak++
	}
	return streak, time.Unix(c.latest*int64((24*time.Hour)/time.Second), 0).UTC()
}

func insertAward(
	ctx context.Context,
	tx pgx.Tx,
	userID uuid.UUID,
	command AwardCommand,
	state State,
) error {
	_, err := tx.Exec(ctx, `
		INSERT INTO gamification_awards (
			user_id, event, idempotency_key, local_date,
			result_streak, result_level, result_xp, result_xp_next,
			result_last_active_date
		) VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9)`,
		userID,
		command.Event,
		command.IdempotencyKey,
		command.LocalDate,
		state.Streak,
		state.Level,
		state.XP,
		state.XPNext,
		state.LastActiveDate,
	)
	if err != nil {
		return fmt.Errorf("record gamification award: %w", err)
	}
	return nil
}

func saveTx(ctx context.Context, tx pgx.Tx, userID uuid.UUID, s State) error {
	command, err := tx.Exec(ctx, `
		UPDATE gamification
		SET streak = $2,
		    level = $3,
		    xp = $4,
		    xp_next = $5,
		    last_active_date = $6
		WHERE user_id = $1`,
		userID, s.Streak, s.Level, s.XP, s.XPNext, s.LastActiveDate)
	if err != nil {
		return fmt.Errorf("save gamification state: %w", err)
	}
	if command.RowsAffected() != 1 {
		return fmt.Errorf("save gamification state: %w", ErrNotFound)
	}
	return nil
}
