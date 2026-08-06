// Package telegram is a thin Bot API client plus the update loop that feeds
// /start payloads into whatever handler auth registers. It knows nothing about
// users or JWTs — it only reports "this Telegram account pressed Start with
// this login code".
package telegram

import (
	"bytes"
	"context"
	"encoding/json"
	"errors"
	"fmt"
	"net/http"
	"time"
)

var ErrNotConfigured = errors.New("telegram bot token not configured")

type Client struct {
	token string
	http  *http.Client
}

func NewClient(token string) *Client {
	if token == "" {
		return nil
	}
	return &Client{token: token, http: &http.Client{Timeout: 65 * time.Second}}
}

// User is the subset of Telegram's User object we persist.
type User struct {
	ID        int64  `json:"id"`
	IsBot     bool   `json:"is_bot"`
	Username  string `json:"username"`
	FirstName string `json:"first_name"`
	LastName  string `json:"last_name"`
}

type Chat struct {
	ID int64 `json:"id"`
}

// Contact is what the request_contact keyboard button sends back. UserID is the
// field that matters: a user can forward somebody else's contact card, and only
// UserID == From.ID proves the number belongs to the sender.
type Contact struct {
	PhoneNumber string `json:"phone_number"`
	FirstName   string `json:"first_name"`
	LastName    string `json:"last_name"`
	UserID      int64  `json:"user_id"`
}

type Message struct {
	MessageID int64    `json:"message_id"`
	From      *User    `json:"from"`
	Chat      Chat     `json:"chat"`
	Text      string   `json:"text"`
	Contact   *Contact `json:"contact"`
}

type Update struct {
	UpdateID int64    `json:"update_id"`
	Message  *Message `json:"message"`
}

func (c *Client) call(ctx context.Context, method string, in, out any) error {
	if c == nil {
		return ErrNotConfigured
	}
	var body []byte
	if in != nil {
		var err error
		if body, err = json.Marshal(in); err != nil {
			return err
		}
	}
	url := fmt.Sprintf("https://api.telegram.org/bot%s/%s", c.token, method)
	req, err := http.NewRequestWithContext(ctx, http.MethodPost, url, bytes.NewReader(body))
	if err != nil {
		return err
	}
	req.Header.Set("Content-Type", "application/json")

	resp, err := c.http.Do(req)
	if err != nil {
		return err
	}
	defer resp.Body.Close()

	var env struct {
		OK          bool            `json:"ok"`
		Description string          `json:"description"`
		Result      json.RawMessage `json:"result"`
	}
	if err := json.NewDecoder(resp.Body).Decode(&env); err != nil {
		return err
	}
	if !env.OK {
		return fmt.Errorf("telegram %s: %s", method, env.Description)
	}
	if out != nil {
		return json.Unmarshal(env.Result, out)
	}
	return nil
}

// Me returns the bot account — used at startup to learn the bot's @username so
// the deep link can be built without a second env var.
func (c *Client) Me(ctx context.Context) (User, error) {
	var u User
	err := c.call(ctx, "getMe", nil, &u)
	return u, err
}

func (c *Client) SendMessage(ctx context.Context, chatID int64, text string) error {
	return c.send(ctx, chatID, text, map[string]any{"remove_keyboard": true})
}

// SendContactRequest shows the one-tap "share my number" keyboard. Telegram only
// allows request_contact in a private chat, which is the only place this bot talks.
func (c *Client) SendContactRequest(ctx context.Context, chatID int64, text, buttonLabel string) error {
	return c.send(ctx, chatID, text, map[string]any{
		"keyboard": []any{
			[]any{map[string]any{"text": buttonLabel, "request_contact": true}},
		},
		"resize_keyboard":   true,
		"one_time_keyboard": true,
	})
}

func (c *Client) send(ctx context.Context, chatID int64, text string, markup any) error {
	return c.call(ctx, "sendMessage", map[string]any{
		"chat_id":      chatID,
		"text":         text,
		"parse_mode":   "HTML",
		"reply_markup": markup,
	}, nil)
}

// SetWebhook points Telegram at url and drops any pending long-poll backlog.
// secret is echoed back in the X-Telegram-Bot-Api-Secret-Token header.
func (c *Client) SetWebhook(ctx context.Context, url, secret string) error {
	return c.call(ctx, "setWebhook", map[string]any{
		"url":             url,
		"secret_token":    secret,
		"allowed_updates": []string{"message"},
	}, nil)
}

// DeleteWebhook is required before getUpdates will work — the two delivery
// modes are mutually exclusive, so a leftover webhook silently starves the poller.
func (c *Client) DeleteWebhook(ctx context.Context) error {
	return c.call(ctx, "deleteWebhook", map[string]any{"drop_pending_updates": false}, nil)
}

func (c *Client) GetUpdates(ctx context.Context, offset int64, timeoutSec int) ([]Update, error) {
	var ups []Update
	err := c.call(ctx, "getUpdates", map[string]any{
		"offset":          offset,
		"timeout":         timeoutSec,
		"allowed_updates": []string{"message"},
	}, &ups)
	return ups, err
}
