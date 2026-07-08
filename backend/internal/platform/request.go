package platform

import (
	"net"
	"net/http"
	"strings"
)

// ClientIP extracts the best-effort client IP for rate-limiting, honouring a
// single proxy hop via X-Forwarded-For. Behind a trusted proxy only.
func ClientIP(r *http.Request) string {
	if xff := r.Header.Get("X-Forwarded-For"); xff != "" {
		parts := strings.Split(xff, ",")
		if ip := strings.TrimSpace(parts[0]); ip != "" {
			return ip
		}
	}
	host, _, err := net.SplitHostPort(r.RemoteAddr)
	if err != nil {
		return r.RemoteAddr
	}
	return host
}
