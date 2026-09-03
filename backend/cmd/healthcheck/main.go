// Command healthcheck probes the API from inside its container without adding
// a shell or HTTP client to the distroless runtime image.
package main

import (
	"io"
	"net/http"
	"os"
	"time"
)

func main() {
	port := os.Getenv("PORT")
	if port == "" {
		port = os.Getenv("HTTP_PORT")
	}
	if port == "" {
		port = "8080"
	}

	path := os.Getenv("HEALTHCHECK_PATH")
	switch path {
	case "":
		path = "/health"
	case "/health", "/ready":
	default:
		os.Exit(1)
	}

	client := http.Client{Timeout: 3 * time.Second}
	resp, err := client.Get("http://127.0.0.1:" + port + path)
	if err != nil {
		os.Exit(1)
	}
	defer resp.Body.Close()
	_, _ = io.Copy(io.Discard, io.LimitReader(resp.Body, 1024))
	if resp.StatusCode != http.StatusOK {
		os.Exit(1)
	}
}
