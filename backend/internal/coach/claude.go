// Package coach proxies the Anthropic Claude Messages API (streaming SSE),
// persists the conversation, and re-streams tokens to the mobile client. The
// API key lives only here, server-side.
package coach

import (
	"bufio"
	"bytes"
	"context"
	"encoding/json"
	"errors"
	"fmt"
	"net/http"
	"strings"
	"time"

	"github.com/tafakkur/backend/internal/config"
)

const anthropicURL = "https://api.anthropic.com/v1/messages"

// SystemPrompt defines Murabbiy's persona.
const SystemPrompt = `You are "Murabbiy", a warm, gentle Islamic reflection and personal-growth coach inside the TafakkurAI app.
Encourage the user toward salah (prayer), gratitude (shukr), and tafakkur (mindful reflection) with compassion and without judgment.
Speak briefly, kindly, and practically. Honour the user's language.
You are NOT a mufti: never issue fatwas or definitive scholarly rulings. For questions of fiqh or religious rulings, gently defer the user to a qualified local scholar or imam.
Never shame the user. Meet them where they are and offer one small, doable next step.`

var ErrNotConfigured = errors.New("anthropic api key not configured")

type Client struct {
	apiKey    string
	model     string
	version   string
	maxTokens int
	http      *http.Client
}

func NewClient(cfg config.Config) *Client {
	return &Client{
		apiKey:    cfg.AnthropicAPIKey,
		model:     cfg.ClaudeModel,
		version:   cfg.AnthropicVersion,
		maxTokens: cfg.ClaudeMaxTokens,
		http:      &http.Client{Timeout: 120 * time.Second},
	}
}

type Message struct {
	Role    string // "user" | "assistant"
	Content string
}

type anthropicMessage struct {
	Role    string `json:"role"`
	Content string `json:"content"`
}

type anthropicRequest struct {
	Model     string             `json:"model"`
	MaxTokens int                `json:"max_tokens"`
	System    string             `json:"system,omitempty"`
	Stream    bool               `json:"stream"`
	Messages  []anthropicMessage `json:"messages"`
}

type streamEvent struct {
	Type  string `json:"type"`
	Delta struct {
		Type string `json:"type"`
		Text string `json:"text"`
	} `json:"delta"`
	Error struct {
		Type    string `json:"type"`
		Message string `json:"message"`
	} `json:"error"`
}

// Stream calls Claude and invokes onDelta for each text chunk. It returns the
// full accumulated assistant text.
func (c *Client) Stream(ctx context.Context, msgs []Message, onDelta func(string) error) (string, error) {
	if c.apiKey == "" {
		return "", ErrNotConfigured
	}

	am := make([]anthropicMessage, 0, len(msgs))
	for _, m := range msgs {
		am = append(am, anthropicMessage{Role: m.Role, Content: m.Content})
	}
	body, err := json.Marshal(anthropicRequest{
		Model:     c.model,
		MaxTokens: c.maxTokens,
		System:    SystemPrompt,
		Stream:    true,
		Messages:  am,
	})
	if err != nil {
		return "", err
	}

	req, err := http.NewRequestWithContext(ctx, http.MethodPost, anthropicURL, bytes.NewReader(body))
	if err != nil {
		return "", err
	}
	req.Header.Set("content-type", "application/json")
	req.Header.Set("x-api-key", c.apiKey)
	req.Header.Set("anthropic-version", c.version)

	resp, err := c.http.Do(req)
	if err != nil {
		return "", fmt.Errorf("anthropic request: %w", err)
	}
	defer resp.Body.Close()

	if resp.StatusCode != http.StatusOK {
		var buf bytes.Buffer
		_, _ = buf.ReadFrom(resp.Body)
		return "", fmt.Errorf("anthropic status %d: %s", resp.StatusCode, buf.String())
	}

	scanner := bufio.NewScanner(resp.Body)
	scanner.Buffer(make([]byte, 0, 64*1024), 1024*1024)

	var sb strings.Builder
	for scanner.Scan() {
		line := scanner.Text()
		if !strings.HasPrefix(line, "data:") {
			continue
		}
		data := strings.TrimSpace(strings.TrimPrefix(line, "data:"))
		if data == "" {
			continue
		}
		var ev streamEvent
		if err := json.Unmarshal([]byte(data), &ev); err != nil {
			continue
		}
		switch ev.Type {
		case "content_block_delta":
			if ev.Delta.Type == "text_delta" && ev.Delta.Text != "" {
				sb.WriteString(ev.Delta.Text)
				if err := onDelta(ev.Delta.Text); err != nil {
					return sb.String(), err
				}
			}
		case "message_stop":
			return sb.String(), nil
		case "error":
			return sb.String(), fmt.Errorf("anthropic stream error: %s", ev.Error.Message)
		}
	}
	return sb.String(), scanner.Err()
}
