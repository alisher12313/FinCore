# FinCore

A production-grade banking microservices system built with Java, Spring Boot, and modern cloud-native technologies. FinCore simulates a real-world fintech backend with multi-currency transfers, event-driven notifications, OAuth2 security, and full observability.

---

## Architecture

```
                        ┌─────────────────────────────┐
                        │         API Gateway          │
                        │    Spring Cloud Gateway      │
                        │         Port 8085            │
                        │    JWT Validation (Keycloak) │
                        └──────────────┬───────────────┘
                                       │
                    ┌──────────────────┼──────────────────┐
                    │                  │                   │
          ┌─────────▼──────┐  ┌────────▼───────┐         │
          │ Account Service│  │Transaction Svc │         │
          │   Port 8080    │  │   Port 8082    │         │
          │   PostgreSQL   │  │   PostgreSQL   │         │
          │   Redis Cache  │  │   MongoDB      │         │
          │   Keycloak JWT │  │   Redis (rate  │         │
          └────────┬───────┘  │    limiting +  │         │
                   │          │   idempotency) │         │
                   │          └────────┬───────┘         │
                   │                   │                  │
                   └──────────┬────────┘                  │
                              │ Kafka                     │
                              │ (account.status.changed)  │
                              │ (transfer.completed)      │
                              ▼                           │
                   ┌──────────────────┐                   │
                   │Notification Svc  │◄──────────────────┘
                   │   Port 8084      │
                   │   MongoDB        │
                   │   Gmail SMTP     │
                   └──────────────────┘
```

---

## Services

| Service | Port | Description | Database |
|---|---|---|---|
| API Gateway | 8085 | Central entry point, JWT validation, routing | — |
| Account Service | 8080 | Account management, multi-currency, caching | PostgreSQL + Redis |
| Transaction Service | 8082 | Transfers, idempotency, rate limiting, audit logs | PostgreSQL + MongoDB + Redis |
| Notification Service | 8084 | Kafka consumer, real email notifications | MongoDB |

---

## Tech Stack

| Category | Technologies |
|---|---|
| Languages | Java 21, SQL |
| Backend | Spring Boot 4.x, Spring Data JPA, Spring Security, Spring Cloud Gateway |
| Messaging | Apache Kafka (2 topics, 3 event types) |
| Caching & Rate Limiting | Redis (idempotency TTL 24h, rate limiting 5 req/min) |
| Databases | PostgreSQL (2 instances), MongoDB (2 instances) |
| Security | Keycloak, OAuth2, JWT (Authorization Code + PKCE, Client Credentials) |
| Service Communication | OpenFeign (Client Credentials M2M) |
| DevOps | Docker, Docker Compose, Liquibase migrations |
| Libraries | MapStruct, Lombok, Hibernate/JPA, Bean Validation, JavaMailSender |

---

## Key Features

- **Multi-currency transfers** — real-time exchange rate conversion via external currency API (KZT, USD, EUR)
- **Idempotent transfers** — duplicate prevention using Redis (TTL 24h) + PostgreSQL fallback
- **Rate limiting** — Redis-based fixed-window counter (5 transfers/min per user)
- **Event-driven notifications** — Kafka async pipeline with dead-letter topic recovery and real Gmail email delivery
- **Dual-database architecture** — PostgreSQL for transactional data, MongoDB for immutable audit logs and notifications
- **OAuth2 security** — Keycloak Authorization Code + PKCE for users, Client Credentials for service-to-service calls
- **Response caching** — Redis `@Cacheable` on account profile reads with `@CacheEvict` on mutations
- **Dynamic filtering** — JPA Specifications for paginated transfer history with status, amount, and date filters

---

## Performance

Load tested with k6 — 100 concurrent users, sustained 30 seconds:

| Metric | Result |
|---|---|
| Requests/sec | ~520 req/s |
| Avg response time | 48ms |
| P95 response time | 112ms |
| P99 response time | 187ms |
| Error rate | 0% |

---

## Event Flow

```
POST /transfers
    │
    ├── Redis idempotency check (fast path)
    ├── Rate limit check (5/min per user)
    ├── Feign → Account Service /internal/transfer (debit/credit)
    ├── Save transfer record → PostgreSQL (DONE)
    ├── Save audit log → MongoDB
    └── Publish transfer.completed → Kafka
                                          │
                              Notification Service
                                  ├── Save notification → MongoDB
                                  └── Send email → Gmail SMTP
```

---

## Getting Started

### Prerequisites

- Docker and Docker Compose
- Java 21 (for local development)
- Maven 3.9+

### Run with Docker Compose

```bash
git clone https://github.com/alisher12313/FinCore.git
cd FinCore
docker compose up --build
```

This starts all infrastructure and services:
- Keycloak at `http://localhost:9090`
- Account Service at `http://localhost:8080`
- Transaction Service at `http://localhost:8082`
- Notification Service at `http://localhost:8084`
- Gateway at `http://localhost:8085`

### Keycloak Setup

After first startup, configure Keycloak:

1. Open `http://localhost:9090` → login with `admin/admin`
2. Create realm: `fincore`
3. Create client: `gateway-client` (Standard flow, PKCE enabled)
4. Create client: `transfer-client` (Service accounts enabled, Client Credentials)
5. Create roles: `user`, `admin`, `service`
6. Assign `service` role to `transfer-client` service account
7. Enable user registration and set `user` as default role

### Environment Variables

Sensitive values should be set via `.env` file (see `.env.example`):

```env
POSTGRES_PASSWORD=your_password
MONGO_PASSWORD=your_password
KEYCLOAK_ADMIN_PASSWORD=your_password
GMAIL_APP_PASSWORD=your_gmail_app_password
CURRENCY_API_KEY=your_api_key
```

---

## API Endpoints

### Account Service (`/account`)

| Method | Path | Auth | Description |
|---|---|---|---|
| POST | `/account` | user | Create account |
| GET | `/account/my` | user | Get own profile |
| POST | `/account/my/balance` | user | Top up balance |
| GET | `/account/my/balance/convert` | user | Convert balance to another currency |
| PATCH | `/account/{id}/freeze` | admin | Freeze account |
| PATCH | `/account/{id}/unfreeze` | admin | Unfreeze account |

### Transaction Service (`/transfer`)

| Method | Path | Auth | Description |
|---|---|---|---|
| POST | `/transfer` | user | Create transfer |
| GET | `/transfer/{id}` | user | Get transfer by ID |
| GET | `/transfer/history` | user | Paginated transfer history with filters |

### Query Parameters for `/transfer/history`

```
?page=0&size=10&status=DONE&minAmount=100&createdAfter=2025-01-01T00:00:00
```

---

## Database Schema

### PostgreSQL — Account Service
```
accounts: id, user_id, account_number, balance, currency, status, email, created_at
```

### PostgreSQL — Transaction Service
```
transfers: id, idempotency_key, from_account_id, to_account_id, amount, currency, status, initiated_by, created_at
```

### MongoDB — Transaction Service
```
audit_logs: _id, transferId, eventType, fromAccountId, toAccountId, amount, initiatedBy, timestamp
```

### MongoDB — Notification Service
```
notifications: _id, userId, type, message, sentAt, status
```

### Redis Keys
```
idempotency:{key}    → transferId string    TTL 24h
rate:{userId}        → request counter      TTL 1min
accounts             → cached account DTO   TTL 10min
```

---

## Design Decisions

**Why MongoDB for audit logs?**
Audit logs are append-only and never updated — MongoDB's document model is a natural fit. Keeping them separate from transactional PostgreSQL data also means audit history survives even if the operational DB is migrated or reset.

**Why Kafka for notifications?**
Notifications are non-critical to the transfer flow — if email delivery fails, the transfer should still succeed. Kafka decouples the two concerns and provides at-least-once delivery guarantees with dead-letter topic recovery for failed events.

**Why idempotency key in both Redis and PostgreSQL?**
Redis is the fast path — O(1) lookup with TTL-based automatic expiry. PostgreSQL is the source of truth and fallback if Redis is cleared or restarted. The combination gives both performance and durability.

**Why separate Redis instances per service?**
Each service owns its own cache/rate-limit data. Shared Redis would create implicit coupling between services and make independent scaling harder.

**Why Client Credentials for Feign?**
Service-to-service calls (Transaction → Account) should not carry the user's JWT — a service should prove its own identity. Client Credentials gives Transaction Service its own token scoped to the `service` role, which Account Service validates independently.

---

## Project Structure

```
FinCore/
├── account-service/          # Account management, caching
├── transaction-service/      # Transfers, Kafka producer, Redis
├── notification-service/     # Kafka consumer, email
├── fincore-gateway/          # API Gateway, JWT validation
├── docker-compose.yaml       # Full infrastructure + services
└── pom.xml                   # Parent POM
```

---

## Author

**Alisher Abden**
- GitHub: [@alisher12313](https://github.com/alisher12313)
- Email: abdenalisher@gmail.com
- LinkedIn: [linkedin.com/in/alisher12313](https://linkedin.com/in/alisher12313)