DATABASE_URL ?= postgres://tafakkur:tafakkur@localhost:5432/tafakkur?sslmode=disable

.PHONY: run build test test-integration lint sqlc tidy docker-up docker-down

run: ## Run the API locally (auto-applies migrations on boot)
	go run ./cmd/api

build: ## Build the single binary
	CGO_ENABLED=0 go build -o bin/api ./cmd/api

test: ## Run unit tests
	go test ./... -count=1

test-integration: ## Run integration tests (needs Docker)
	go test -tags=integration ./... -count=1

lint: ## Vet + golangci-lint (if installed)
	go vet ./...
	@command -v golangci-lint >/dev/null 2>&1 && golangci-lint run || echo "golangci-lint not installed, skipping"

tidy:
	go mod tidy

sqlc: ## Regenerate typed queries (optional; repos currently use pgx directly)
	sqlc generate

docker-up: ## Bring up api + postgres + minio
	docker compose up --build -d

docker-down:
	docker compose down
