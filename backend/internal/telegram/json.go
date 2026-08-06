package telegram

import (
	"encoding/json"
	"io"
	"net/http"
)

const maxUpdateBytes = 1 << 20

func decodeJSON(r *http.Request, v any) error {
	return json.NewDecoder(io.LimitReader(r.Body, maxUpdateBytes)).Decode(v)
}
