module github.com/tafakkur/backend

go 1.23

require (
	github.com/aws/aws-sdk-go-v2 v1.30.3
	github.com/aws/aws-sdk-go-v2/config v1.27.27
	github.com/aws/aws-sdk-go-v2/credentials v1.17.27
	github.com/aws/aws-sdk-go-v2/feature/s3/manager v1.17.16
	github.com/aws/aws-sdk-go-v2/service/s3 v1.58.2
	github.com/caarlos0/env/v11 v11.2.0
	github.com/go-chi/chi/v5 v5.1.0
	github.com/go-playground/validator/v10 v10.22.0
	github.com/golang-jwt/jwt/v5 v5.2.1
	github.com/google/uuid v1.6.0
	github.com/jackc/pgx/v5 v5.6.0
	golang.org/x/crypto v0.27.0
)

require (
	github.com/testcontainers/testcontainers-go v0.33.0
	github.com/testcontainers/testcontainers-go/modules/postgres v0.33.0
)

// Run `go mod tidy` on a CLEAN machine to resolve the indirect dependency graph.
