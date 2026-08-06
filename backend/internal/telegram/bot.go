package telegram

import (
	"context"
	"fmt"
	"log/slog"
	"net/http"
	"strings"
	"time"

	"github.com/jackc/pgx/v5/pgxpool"
)

// ContactHandler is what auth implements: the user shared a verified phone
// number, so mint the confirmation code they will type into the app.
type ContactHandler func(ctx context.Context, from User, contact Contact) (code string, err error)

// Bot turns raw updates into StartHandler calls. It is delivery-agnostic: the
// same Dispatch runs behind the webhook handler and behind the long poller.
type Bot struct {
	client    *Client
	repo      *Repo
	onContact ContactHandler
	// Username of the bot itself, resolved once via getMe. Empty until Init runs.
	username string
	// CodeTTL is only used for the wording of the code message; auth owns the clock.
	CodeTTL time.Duration
}

func NewBot(c *Client, repo *Repo, onContact ContactHandler) *Bot {
	return &Bot{client: c, repo: repo, onContact: onContact}
}

func (b *Bot) Enabled() bool { return b != nil && b.client != nil }

func (b *Bot) Username() string {
	if b == nil {
		return ""
	}
	return b.username
}

// Init resolves the bot's @username so deep links can be built server-side.
func (b *Bot) Init(ctx context.Context) error {
	if !b.Enabled() {
		return ErrNotConfigured
	}
	me, err := b.client.Me(ctx)
	if err != nil {
		return err
	}
	b.username = me.Username
	slog.Info("telegram bot connected", "username", me.Username, "id", me.ID)
	return nil
}

const (
	askContact = "Assalomu alaykum! 👋\n\nTafakkur AI ga kirish uchun raqamingizni ulashing — pastdagi tugmani bosing. Raqam faqat hisobingizni tanish uchun ishlatiladi."
	btnContact = "📱 Raqamni ulashish"
	// A forwarded contact card carries somebody else's user_id. Accepting it would
	// let anyone sign in as a person whose number they happen to have saved.
	replyForeignContact = "Bu raqam sizniki emas. Iltimos, tugma orqali <b>oʻz</b> raqamingizni ulashing."
	replyFailure        = "Kod yaratilmadi. Biroz kutib, /start ni qayta bosing."
	replyHint           = "Kirish uchun /start ni bosing va raqamingizni ulashing."
)

func codeMessage(code string, ttl time.Duration) string {
	return fmt.Sprintf(
		"Tasdiqlash kodingiz:\n\n<code>%s</code>\n\nUni ilovadagi maydonga kiriting. Kod %d daqiqa amal qiladi.",
		code, int(ttl.Minutes()),
	)
}

// Dispatch processes a single update. Errors are logged, not returned: Telegram
// retries an update it thinks failed, and a poison message would loop forever.
func (b *Bot) Dispatch(ctx context.Context, u Update) {
	msg := u.Message
	if msg == nil || msg.From == nil || msg.From.IsBot {
		return
	}

	if c := msg.Contact; c != nil {
		if c.UserID != msg.From.ID {
			b.reply(ctx, msg.Chat.ID, replyForeignContact)
			return
		}
		code, err := b.onContact(ctx, *msg.From, *c)
		if err != nil {
			slog.Warn("telegram: issue auth code", "error", err, "telegram_id", msg.From.ID)
			b.reply(ctx, msg.Chat.ID, replyFailure)
			return
		}
		b.reply(ctx, msg.Chat.ID, codeMessage(code, b.CodeTTL))
		return
	}

	if strings.HasPrefix(strings.TrimSpace(msg.Text), "/start") {
		if err := b.client.SendContactRequest(ctx, msg.Chat.ID, askContact, btnContact); err != nil {
			slog.Warn("telegram: send contact request", "error", err, "chat_id", msg.Chat.ID)
		}
		return
	}

	b.reply(ctx, msg.Chat.ID, replyHint)
}

func (b *Bot) reply(ctx context.Context, chatID int64, text string) {
	if err := b.client.SendMessage(ctx, chatID, text); err != nil {
		slog.Warn("telegram sendMessage failed", "error", err, "chat_id", chatID)
	}
}

// WebhookHandler serves Telegram's POSTs. secret must match the token passed to
// SetWebhook — without that check the endpoint is an open update injector.
func (b *Bot) WebhookHandler(secret string) http.HandlerFunc {
	return func(w http.ResponseWriter, r *http.Request) {
		if secret != "" && r.Header.Get("X-Telegram-Bot-Api-Secret-Token") != secret {
			w.WriteHeader(http.StatusUnauthorized)
			return
		}
		var u Update
		if err := decodeJSON(r, &u); err != nil {
			w.WriteHeader(http.StatusBadRequest)
			return
		}
		// Answer immediately; Telegram's delivery timeout is short and the work
		// below is a couple of DB round-trips plus a sendMessage.
		go b.Dispatch(context.WithoutCancel(r.Context()), u)
		w.WriteHeader(http.StatusOK)
	}
}

// RunPoller is the no-public-URL path (local dev): long-poll getUpdates until
// ctx is cancelled. The consumed offset is persisted so a restart never replays
// an old /start and re-approves a stale login code.
func (b *Bot) RunPoller(ctx context.Context) {
	if err := b.client.DeleteWebhook(ctx); err != nil {
		slog.Warn("telegram deleteWebhook failed", "error", err)
	}
	offset, err := b.repo.LastUpdate(ctx)
	if err != nil {
		slog.Error("telegram: read offset", "error", err)
		return
	}
	slog.Info("telegram long-poller started", "offset", offset)

	for {
		if ctx.Err() != nil {
			return
		}
		ups, err := b.client.GetUpdates(ctx, offset+1, 50)
		if err != nil {
			if ctx.Err() != nil {
				return
			}
			slog.Warn("telegram getUpdates failed", "error", err)
			select {
			case <-ctx.Done():
				return
			case <-time.After(3 * time.Second):
			}
			continue
		}
		for _, u := range ups {
			b.Dispatch(ctx, u)
			if u.UpdateID > offset {
				offset = u.UpdateID
			}
		}
		if len(ups) > 0 {
			if err := b.repo.SetLastUpdate(ctx, offset); err != nil {
				slog.Warn("telegram: persist offset", "error", err)
			}
		}
	}
}

type Repo struct{ pool *pgxpool.Pool }

func NewRepo(pool *pgxpool.Pool) *Repo { return &Repo{pool: pool} }

func (r *Repo) LastUpdate(ctx context.Context) (int64, error) {
	var v int64
	err := r.pool.QueryRow(ctx, `SELECT last_update FROM telegram_bot_state WHERE id = 1`).Scan(&v)
	return v, err
}

func (r *Repo) SetLastUpdate(ctx context.Context, v int64) error {
	_, err := r.pool.Exec(ctx, `UPDATE telegram_bot_state SET last_update = $1 WHERE id = 1`, v)
	return err
}
