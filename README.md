# MisterPilot Platform — Backend Service

**`platform.misterpilot.online`**

The backend service powering the MisterPilot platform — an API gateway and billing layer for DeepSeek AI models. This service is the **source of truth** for users, API keys, wallets, payments, usage tracking, and dashboard analytics.

> **Architecture Note:** This backend does NOT communicate with DeepSeek directly. A separate **Python Gateway** handles the actual AI model proxying and calls back to this service for key verification and usage billing via internal endpoints.

---

## Table of Contents

- [Overview](#overview)
- [Technology Stack](#technology-stack)
- [Project Structure](#project-structure)
- [Domain Model](#domain-model)
  - [Entity Relationship Diagram](#entity-relationship-diagram)
  - [User](#user)
  - [ApiKey](#apikey)
  - [Wallet](#wallet)
  - [Transaction](#transaction)
  - [TokenUsage](#tokenusage)
  - [PasswordResetToken](#passwordresettoken)
- [API Endpoints](#api-endpoints)
  - [Authentication (`/api/v1/auth`)](#authentication-apiv1auth)
  - [API Keys (`/api/v1/keys`)](#api-keys-apiv1keys)
  - [Wallet (`/api/v1/wallet`)](#wallet-apiv1wallet)
  - [Stats & Dashboard (`/api/v1/stats`)](#stats--dashboard-apiv1stats)
  - [Profile (`/api/v1/profile`)](#profile-apiv1profile)
  - [Internal Usage (`/internal/usage`)](#internal-usage-internalusage)
- [Security](#security)
  - [Authentication Flow](#authentication-flow)
  - [JWT Details](#jwt-details)
  - [API Key Security](#api-key-security)
  - [Route Protection](#route-protection)
- [Business Logic](#business-logic)
  - [Pricing & Cost Calculation](#pricing--cost-calculation)
  - [Wallet Operations](#wallet-operations)
  - [Payment Flow (Razorpay)](#payment-flow-razorpay)
  - [Password Reset Flow](#password-reset-flow)
  - [Account Deletion](#account-deletion)
- [Configuration](#configuration)
  - [Profiles](#profiles)
  - [Key Properties](#key-properties)
- [Database](#database)
  - [Migrations (Flyway)](#migrations-flyway)
  - [JPA/Hibernate Notes](#jpahibernate-notes)
- [Getting Started](#getting-started)
  - [Prerequisites](#prerequisites)
  - [Setup](#setup)
  - [Running](#running)
  - [Testing](#testing)
- [Error Handling](#error-handling)
- [Design Principles](#design-principles)

---

## Overview

MisterPilot Platform is a **monolithic Spring Boot application** that provides:

| Responsibility | Description |
|---|---|
| **User Management** | Email/password and Google OAuth registration/login, profile management, account deletion |
| **API Key Management** | Generate, verify, list, and disable API keys. Keys are SHA-256 hashed — plain text never stored |
| **Wallet Management** | Per-user wallet with INR balance. Minimum ₹5 balance required for API key usage |
| **Payment Processing** | Razorpay integration for wallet recharges (order creation → signature verification → credit) |
| **Usage Tracking & Billing** | Per-request token tracking, model-specific cost calculation, automatic wallet debiting |
| **Dashboard & Analytics** | User stats, date-wise breakdowns, wallet summaries, usage history |
| **Email Notifications** | Password reset links, recharge confirmations (async via `@Async`) |

---

## Technology Stack

| Category | Technology | Version |
|---|---|---|
| **Language** | Java | 21 |
| **Framework** | Spring Boot | 3.5.14 |
| **Build Tool** | Maven | — |
| **Database** | PostgreSQL | — |
| **ORM** | Spring Data JPA + Hibernate | — |
| **Migrations** | Flyway | — |
| **Security** | Spring Security + JWT (jjwt) | 0.12.6 |
| **OAuth** | Google OAuth2 (tokeninfo endpoint) | — |
| **Payments** | Razorpay Java SDK | 1.4.8 |
| **Email** | Spring Boot Starter Mail (JavaMailSender) | — |
| **Boilerplate Reduction** | Lombok | — |
| **Password Hashing** | BCrypt (Spring Security) | — |
| **API Key Hashing** | SHA-256 (deterministic for DB lookup) | — |

---

## Project Structure

```
platform/
├── pom.xml
├── README.md
├── schema.md                          # Original schema design document
├── misterpilot.md                     # Development context & implementation log
├── src/
│   ├── main/
│   │   ├── java/online/misterpilot/platform/
│   │   │   ├── PlatformApplication.java          # Entry point (@SpringBootApplication, @EnableAsync)
│   │   │   ├── auth/
│   │   │   │   └── AuthController.java           # Auth endpoints (login, register, Google, password reset)
│   │   │   ├── config/
│   │   │   │   ├── SecurityConfig.java           # Spring Security filter chain configuration
│   │   │   │   ├── JwtAuthenticationFilter.java  # OncePerRequestFilter: JWT extraction & validation
│   │   │   │   ├── GlobalExceptionHandler.java   # @RestControllerAdvice: centralized error handling
│   │   │   │   ├── GoogleProperties.java         # @ConfigurationProperties for Google OAuth
│   │   │   │   └── RazorpayProperties.java       # @ConfigurationProperties + RazorpayClient bean
│   │   │   ├── controller/
│   │   │   │   ├── KeyController.java            # API key management endpoints
│   │   │   │   ├── WalletController.java         # Wallet / payment endpoints
│   │   │   │   ├── StatsController.java          # Dashboard & stats endpoints
│   │   │   │   ├── ProfileController.java        # User profile endpoints
│   │   │   │   └── UsageController.java          # Internal: usage charging endpoint
│   │   │   ├── dto/
│   │   │   │   ├── request/                      # 12 request DTOs
│   │   │   │   └── response/                     # 12 response DTOs
│   │   │   ├── entity/
│   │   │   │   ├── User.java                     # users table
│   │   │   │   ├── ApiKey.java                   # api_keys table
│   │   │   │   ├── Wallet.java                   # wallets table
│   │   │   │   ├── Transaction.java              # transactions table
│   │   │   │   ├── TokenUsage.java               # token_usage table
│   │   │   │   └── PasswordResetToken.java       # password_reset_tokens table
│   │   │   ├── enums/
│   │   │   │   ├── TransactionType.java          # RECHARGE, USAGE_CHARGE, REFUND, ADJUSTMENT
│   │   │   │   └── TransactionStatus.java        # PENDING, SUCCESS, FAILED
│   │   │   ├── repository/                       # 6 Spring Data JPA repositories
│   │   │   ├── service/                          # 10 service classes
│   │   │   └── util/
│   │   │       ├── AuthUtil.java                 # JWT generation/validation, BCrypt, current user
│   │   │       └── KeyUtil.java                  # API key generation, SHA-256 hashing, placeholders
│   │   └── resources/
│   │       ├── application.properties            # Base config (port, active profile)
│   │       ├── application-dev.properties        # Dev: local DB, debug SQL, test Razorpay keys
│   │       ├── application-prod.properties       # Prod: production DB, live Razorpay keys
│   │       └── db/migration/
│   │           └── V1__make_transaction_wallet_id_nullable.sql
│   └── test/
│       └── java/online/misterpilot/platform/
│           └── PlatformApplicationTests.java     # Context load test
```

---

## Domain Model

### Entity Relationship Diagram

```
┌──────────┐       ┌───────────┐       ┌──────────────┐
│   User   │1─────1│  Wallet   │1─────*│ Transaction  │
│          │       │           │       │              │
│  id (PK) │       │ id (PK)   │       │ id (PK)      │
│  name    │       │ user (FK) │       │ wallet (FK)  │
│  email   │       │ balance   │       │ type (enum)  │
│  googleId│       │ updatedAt │       │ status (enum)│
│  passHash│       └───────────┘       │ amount       │
│  createdAt│                          │ orderId      │
└──────────┘                           │ paymentId    │
     │                                 │ createdAt    │
     │ 1                              └──────────────┘
     │ *
┌──────────┐       ┌──────────────┐
│  ApiKey  │1─────*│ TokenUsage   │
│          │       │              │
│ id (PK)  │       │ id (PK)      │
│ user (FK)│       │ apiKey (FK)  │
│ hashValue│       │ user (FK)    │
│ placehold│       │ model        │
│ active   │       │ outputTokens │
│ lastUsed │       │ cacheHitToks │
│ createdAt│       │ cacheMissToks│
└──────────┘       │ costUsd      │
                   │ costInr      │
┌────────────────┐ │ createdAt    │
│PasswordResetTkn│ └──────────────┘
│ id (PK)        │
│ email          │
│ token          │
│ expiresAt      │
│ used           │
│ createdAt      │
└────────────────┘
```

### User

Represents a registered user. Supports both email/password and Google OAuth authentication (or linking both).

| Column | Type | Constraints | Notes |
|---|---|---|---|
| `id` | `BIGSERIAL` | PRIMARY KEY | |
| `google_id` | `VARCHAR` | UNIQUE, NULLABLE | Set for Google OAuth users |
| `name` | `VARCHAR(255)` | NOT NULL | |
| `email` | `VARCHAR(255)` | UNIQUE, NOT NULL | |
| `password_hash` | `VARCHAR(255)` | NULLABLE | BCrypt hash; NULL for Google-only users |
| `created_at` | `TIMESTAMP` | NOT NULL | Auto-set via `@PrePersist` |

**Relationships:**
- One-to-One → `Wallet` (cascade ALL, orphan removal)
- One-to-Many → `ApiKey` (cascade ALL, orphan removal)

### ApiKey

Represents a MisterPilot API key. Only the SHA-256 hash is stored — the plain key is returned exactly once at generation time.

| Column | Type | Constraints | Notes |
|---|---|---|---|
| `id` | `BIGSERIAL` | PRIMARY KEY | |
| `hash_value` | `VARCHAR(255)` | UNIQUE, NOT NULL | SHA-256 hex of the plain key |
| `key_placeholder` | `VARCHAR(30)` | NOT NULL | Display-only: `mp_sk_a3f2c8b1****c4b7` |
| `user_id` | `BIGINT` | FK → users(id), NOT NULL | |
| `active` | `BOOLEAN` | NOT NULL, DEFAULT true | Can be disabled |
| `last_used_at` | `TIMESTAMP` | NULLABLE | |
| `created_at` | `TIMESTAMP` | NOT NULL | Auto-set via `@PrePersist` |

**Key Format:** `mp_sk_` + 48 random hex characters (24 bytes from `SecureRandom`)

**Key Security:**
- Plain key is **never stored** — only `SHA-256(plainKey)` is persisted
- SHA-256 is deterministic (unlike BCrypt) enabling `findByHashValue()` DB lookup
- 48 random bytes = ~384 bits of entropy — brute-force infeasible
- Verification uses constant-time `MessageDigest.isEqual()` comparison

### Wallet

Per-user balance in INR. Created automatically at user registration with a zero balance.

| Column | Type | Constraints | Notes |
|---|---|---|---|
| `id` | `BIGSERIAL` | PRIMARY KEY | |
| `user_id` | `BIGINT` | FK → users(id), UNIQUE, NOT NULL | One wallet per user |
| `balance` | `DECIMAL(12,2)` | NOT NULL, DEFAULT 0.00 | |
| `updated_at` | `TIMESTAMP` | NOT NULL | Auto-updated via `@PreUpdate` |

**Rules:**
- Balance must never go negative
- All deductions must be transactional (pessimistic write lock with 3s timeout on debit operations)
- Minimum ₹5 balance required for API key usage

### Transaction

Complete financial audit trail. Every balance change creates a transaction record.

| Column | Type | Constraints | Notes |
|---|---|---|---|
| `id` | `BIGSERIAL` | PRIMARY KEY | |
| `wallet_id` | `BIGINT` | FK → wallets(id), NULLABLE | Nullable for detached records on account deletion |
| `type` | `VARCHAR(50)` | NOT NULL | `RECHARGE`, `USAGE_CHARGE`, `REFUND`, `ADJUSTMENT` |
| `status` | `VARCHAR(50)` | NOT NULL | `PENDING`, `SUCCESS`, `FAILED` |
| `amount` | `DECIMAL(12,2)` | NOT NULL | Always positive (type indicates direction) |
| `payment_id` | `VARCHAR(255)` | UNIQUE, NULLABLE | Razorpay payment ID |
| `order_id` | `VARCHAR(255)` | NULLABLE | Razorpay order ID |
| `created_at` | `TIMESTAMP` | NOT NULL | Auto-set via `@PrePersist` |

**Replay Attack Prevention:** Each `paymentId` is checked — if already associated with a SUCCESS transaction, the request is rejected.

### TokenUsage

Per-request token consumption record tied to an API key and user. Used for billing transparency and analytics.

| Column | Type | Constraints | Notes |
|---|---|---|---|
| `id` | `BIGSERIAL` | PRIMARY KEY | |
| `api_key_id` | `BIGINT` | FK → api_keys(id), NOT NULL | |
| `user_id` | `BIGINT` | FK → users(id), NOT NULL | Denormalized for faster queries |
| `model` | `VARCHAR(100)` | NOT NULL | `deepseek-v4-pro` or `deepseek-v4-flash` |
| `output_tokens` | `BIGINT` | NOT NULL, DEFAULT 0 | |
| `cache_hit_tokens` | `BIGINT` | NOT NULL, DEFAULT 0 | Prompt tokens served from KV-cache |
| `cache_miss_tokens` | `BIGINT` | NOT NULL, DEFAULT 0 | Fresh prompt tokens needing computation |
| `cost_usd` | `DECIMAL(12,8)` | NOT NULL | USD cost (high precision for micro-billing) |
| `cost_inr` | `DECIMAL(12,2)` | NOT NULL | INR cost after conversion |
| `created_at` | `TIMESTAMP` | NOT NULL | Auto-set via `@PrePersist` |

**Note:** TokenUsage is only recorded for MisterPilot keys. DeepSeek-native keys (starting with `sk`) are passed through with cost calculation only — no wallet lookup or usage recording.

### PasswordResetToken

Time-limited, single-use password reset tokens.

| Column | Type | Constraints | Notes |
|---|---|---|---|
| `id` | `BIGSERIAL` | PRIMARY KEY | |
| `email` | `VARCHAR` | NOT NULL | |
| `token` | `VARCHAR` | UNIQUE, NOT NULL | UUID-based random token |
| `expires_at` | `TIMESTAMP` | NOT NULL | 15-minute expiry from creation |
| `used` | `BOOLEAN` | NOT NULL, DEFAULT false | Prevents token reuse |
| `created_at` | `TIMESTAMP` | NOT NULL | Auto-set via `@PrePersist` |

---

## API Endpoints

### Authentication (`/api/v1/auth`)

All endpoints are **public** (no JWT required).

| Method | Path | Request Body | Response | Description |
|---|---|---|---|---|
| `POST` | `/login` | `{ email, password }` | `LoginResponse` | Email/password login. Google-only users get a specific error. |
| `POST` | `/register` | `{ name, email, password }` | `LoginResponse` | Register new user. Creates wallet automatically. |
| `POST` | `/google/login` | `{ idToken }` | `LoginResponse` | Google OAuth. Links to existing email user or creates new account. |
| `POST` | `/forgot-password` | `{ email }` | `{ message }` | Sends reset link via email (async). Always returns success to prevent enumeration. |
| `POST` | `/reset-password` | `{ token, newPassword }` | `{ message }` | Resets password. Validates token expiry and one-time use. |

**LoginResponse:** `{ token (JWT), userId, name, email }`

### API Keys (`/api/v1/keys`)

| Method | Path | Auth | Request Body | Response | Description |
|---|---|---|---|---|---|
| `POST` | `/generate` | JWT | — | `GenerateKeyResponse` | Generates new API key. **Returns plain key ONCE.** |
| `POST` | `/verify` | **Public** | `{ apiKey }` | `VerifyKeyResponse` | Called by Python Gateway. Checks key exists, is active, user has ≥₹5. |
| `GET` | `/active` | JWT | — | `List<ApiKeyResponse>` | List active keys with placeholders (not plain keys). |
| `POST` | `/disable` | JWT | `{ apiKeyId }` | `DisableKeyResponse` | Deactivates a key owned by the authenticated user. |

### Wallet (`/api/v1/wallet`)

| Method | Path | Auth | Request Body | Response | Description |
|---|---|---|---|---|---|
| `POST` | `/create-order` | JWT (CORS: *) | `{ amount }` | `CreateOrderResponse` | Creates Razorpay order + PENDING transaction. Returns data for checkout. |
| `POST` | `/credit` | JWT | `{ orderId, paymentId, signature }` | `TransactionResponse` | Verifies signature, confirms payment, credits wallet. |
| `POST` | `/fail-payment` | JWT | `{ orderId }` | `{ message }` | Marks PENDING transaction as FAILED. No-op if already SUCCESS. |

### Stats & Dashboard (`/api/v1/stats`)

| Method | Path | Auth | Query Params | Response | Description |
|---|---|---|---|---|---|
| `GET` | `/` | JWT | — | `UserStatsResponse` | Full dashboard: balance, total requests, total tokens, total cost, date-wise breakdowns, last 10 usages. |
| `GET` | `/date` | JWT | `?date=YYYY-MM-DD` | `UserStatsForDateResponse` | Tokens consumed & cost for a specific date. |
| `GET` | `/keys` | JWT | — | `List<ApiKeyResponse>` | All API keys for the user. |
| `GET` | `/wallet-summary` | JWT | — | `WalletSummaryResponse` | Balance, total recharged, total consumed, last 5 recharges. |

### Profile (`/api/v1/profile`)

| Method | Path | Auth | Response | Description |
|---|---|---|---|---|
| `GET` | `/me` | JWT | `ProfileResponse` | Returns email, name, join date. |
| `DELETE` | `/me` | JWT | `{ message }` | **Permanently deletes** account: detaches transactions, deletes token usage, API keys, wallet, and user. |

### Internal Usage (`/internal/usage`)

**Public** (called by the Python Gateway, not end users).

| Method | Path | Request Body | Response | Description |
|---|---|---|---|---|
| `POST` | `/charge` | `UsageChargeRequest` | `CostCalculationResponse` | Calculates cost & charges wallet for MisterPilot keys. Cost-only for DeepSeek keys. |

**UsageChargeRequest:** `{ apiKey, outputTokens, cacheHitTokens, cacheMissTokens, model }`

---

## Security

### Authentication Flow

1. **Login/Register** → Returns a signed JWT (7-day expiry)
2. **Subsequent requests** → Include `Authorization: Bearer <jwt>` header
3. **JwtAuthenticationFilter** (OncePerRequestFilter):
   - Extracts Bearer token from the `Authorization` header
   - Validates token (signature, expiry, structure)
   - Looks up the User by `sub` claim (user ID)
   - Sets `SecurityContextHolder` with the full `User` entity as principal

### JWT Details

| Claim | Value |
|---|---|
| `sub` | User ID (string) |
| `email` | User email |
| `name` | User display name |
| `googleId` | Google ID (null for email/password users) |
| `iat` | Issued at |
| `exp` | 7 days after issue |

- Signed with **HMAC-SHA** using a configurable secret (`app.auth.jwt-secret`)
- Validated for: signature correctness, expiry, malformation

### API Key Security

- **Generation:** 24 random bytes → `mp_sk_<48 hex chars>` (SecureRandom)
- **Storage:** Only `SHA-256(plainKey)` stored — deterministic for DB lookup
- **Display:** Placeholder format `mp_sk_<first 8>****<last 4>`
- **Verification:** Constant-time comparison via `MessageDigest.isEqual()`
- **Why SHA-256 (not BCrypt)?** BCrypt salts are random per hash, making `findByHashValue()` impossible. API keys have 384 bits of entropy — brute-force is infeasible even without salting.

### Route Protection

```
┌─────────────────────────────────────────────────────┐
│ Security Filter Chain (STATELESS)                   │
│                                                     │
│  PUBLIC (no JWT):                                   │
│    /api/v1/auth/**          → Auth endpoints        │
│    /api/v1/auth/google/**   → Google OAuth          │
│    /api/v1/keys/verify      → Key verification      │
│    /internal/**             → Python Gateway calls  │
│                                                     │
│  AUTHENTICATED (JWT required):                      │
│    Everything else                                  │
└─────────────────────────────────────────────────────┘
```

- CSRF disabled (stateless API)
- No form login, no HTTP Basic
- Session management: `STATELESS`

---

## Business Logic

### Pricing & Cost Calculation

The `CostCalculatorService` applies model-specific pricing with an optional 30% profit margin.

#### Model Pricing (per token, USD)

| Model | Output Token | Cache Hit Token | Cache Miss Token |
|---|---|---|---|
| `deepseek-v4-pro` | $0.00000087 | $0.000000003625 | $0.000000435 |
| `deepseek-v4-flash` | $0.00000028 | $0.0000000028 | $0.00000014 |

#### Key Type Logic

| Key Prefix | Type | Behavior |
|---|---|---|
| `mp` | MisterPilot | 30% profit margin applied, wallet debited, usage recorded |
| `sk` | DeepSeek (native) | Raw cost only, no wallet interaction, no usage recording |

#### Cost Formula

```
rawCost     = (outputTokens × outputRate) + (cacheHitTokens × cacheHitRate) + (cacheMissTokens × cacheMissRate)
finalCostUsd = rawCost × (1 + margin)   [for MisterPilot keys only]
costInr     = finalCostUsd × inrRate     [configurable, default 96.0]
```

### Wallet Operations

#### Recharge (Razorpay)

1. Frontend calls `POST /api/v1/wallet/create-order` with amount
2. Backend creates Razorpay order and a PENDING transaction
3. Frontend opens Razorpay checkout with returned `orderId` + `keyId`
4. On success, frontend calls `POST /api/v1/wallet/credit` with `{ orderId, paymentId, signature }`
5. Backend:
   - Verifies Razorpay signature
   - Fetches payment from Razorpay (confirms "captured" status)
   - Guards against replay (checks paymentId not already SUCCESS)
   - Credits wallet balance under **pessimistic write lock** (3s timeout)
   - Marks transaction SUCCESS
   - Sends email confirmation (async)

**Minimum recharge:** ₹50

#### Usage Deduction

1. Python Gateway calls `POST /internal/usage/charge`
2. If MisterPilot key: calculates cost with margin, debits wallet under pessimistic write lock, records `TokenUsage`
3. If DeepSeek key: calculates cost only, returns breakdown (no wallet interaction)

**Minimum balance for key usage:** ₹5

### Password Reset Flow

1. User submits email → `POST /api/v1/auth/forgot-password`
2. Backend invalidates any existing tokens for that email
3. Generates UUID token, stores in `password_reset_tokens` (15 min expiry)
4. Sends email with link: `{frontendUrl}/reset-password?token={token}` (async)
5. User clicks link → frontend calls `POST /api/v1/auth/reset-password` with `{ token, newPassword }`
6. Backend validates: token exists, not used, not expired, password ≥ 8 chars
7. Updates password hash, marks token as used

### Account Deletion

`DELETE /api/v1/profile/me` performs a cascading cleanup:

1. Detach transactions from wallet (`SET wallet_id = NULL`)
2. Delete all `TokenUsage` records for the user
3. Delete all `ApiKey` records for the user
4. Delete the `Wallet`
5. Delete the `User`

---

## Configuration

### Profiles

| Profile | Config File | Usage |
|---|---|---|
| `dev` (default) | `application-dev.properties` | Local development: `localhost:5432/misterpilot_platform`, test Razorpay keys, SQL logging |
| `prod` | `application-prod.properties` | Production: production DB, live Razorpay keys, `ddl-auto=validate` |

### Key Properties

| Property | Description |
|---|---|
| `server.port` | HTTP port (default: 8080) |
| `spring.datasource.*` | PostgreSQL connection details |
| `spring.jpa.hibernate.ddl-auto` | `update` (dev) / `validate` (prod) |
| `spring.flyway.enabled` | Flyway migration toggle |
| `app.auth.jwt-secret` | HMAC-SHA signing key for JWTs |
| `google.client-id` | Google OAuth client ID |
| `razorpay.key-id` | Razorpay API key ID |
| `razorpay.key-secret` | Razorpay API key secret |
| `spring.mail.*` | SMTP settings (Gmail with app password) |
| `app.mail.from` | Sender address for emails |
| `app.frontend.url` | Frontend URL for password reset links |
| `app.billing.inr-rate` | USD → INR conversion rate (default: 96.0) |
| `app.billing.scale` | USD decimal scale (default: 8) |

---

## Database

### Migrations (Flyway)

| Version | File | Description |
|---|---|---|
| V1 | `V1__make_transaction_wallet_id_nullable.sql` | Allows `wallet_id` to be NULL (for account deletion detach) |

### JPA/Hibernate Notes

- **Development:** `ddl-auto=update` — Hibernate auto-creates/updates schema
- **Production:** `ddl-auto=validate` — Hibernate validates entities match schema; Flyway manages migrations
- **Wallet debit operations** use `@Lock(PESSIMISTIC_WRITE)` with 3-second timeout to prevent race conditions
- All service methods dealing with data mutations are `@Transactional`

---

## Getting Started

### Prerequisites

- **Java 21**
- **Maven 3.9+**
- **PostgreSQL 15+** (running on `localhost:5432`)
- **SMTP server** (Gmail with app password recommended for dev)

### Setup

1. **Clone the repository:**
   ```bash
   git clone <repo-url>
   cd platform
   ```

2. **Create the database:**
   ```sql
   CREATE DATABASE misterpilot_platform;
   ```

3. **Configure `application-dev.properties`:**
   - Set `spring.datasource.password` to your PostgreSQL password
   - Set `app.auth.jwt-secret` to a secure random string (≥32 chars)
   - Set `google.client-id` (from Google Cloud Console)
   - Set `razorpay.key-id` and `razorpay.key-secret` (from Razorpay Dashboard — test mode)
   - Set `spring.mail.username` and `spring.mail.password` (Gmail app password)
   - Set `app.mail.from` to the same email

4. **(Optional) Set environment variables** for secrets instead of hardcoding in properties files.

### Running

```bash
# Development (active profile: dev)
./mvnw spring-boot:run

# Or with explicit profile
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev

# Production
java -jar target/platform-0.0.1-SNAPSHOT.jar --spring.profiles.active=prod
```

The server starts on **`http://localhost:8080`**.

### Testing

```bash
./mvnw test
```

Currently includes a basic Spring context load test.

---

## Error Handling

All errors are handled by `GlobalExceptionHandler` (`@RestControllerAdvice`) and return a consistent `ApiErrorResponse`:

```json
{
  "timestamp": "2025-07-14T10:30:00Z",
  "status": 400,
  "error": "Bad Request",
  "message": "Invalid email or password",
  "path": "/api/v1/auth/login"
}
```

| HTTP Status | Exception | Scenario |
|---|---|---|
| `400` | `IllegalArgumentException` | Validation errors, bad input |
| `400` | `MethodArgumentNotValidException` | Bean validation failures (with field-level details) |
| `400` | `HttpMessageNotReadableException` | Malformed JSON |
| `401` | `AuthenticationException` | Missing/invalid JWT |
| `403` | `AccessDeniedException` | Insufficient permissions |
| `404` | `NoResourceFoundException` | Unknown endpoint |
| `405` | `HttpRequestMethodNotSupportedException` | Wrong HTTP method |
| `409` | `IllegalStateException` | Conflict (e.g., duplicate payment, wrong user) |
| `409` | `DataIntegrityViolationException` | Database constraint violation |
| `501` | `UnsupportedOperationException` | Not implemented |
| `502` | `HttpClientErrorException` / `RestClientException` | External API failure (Google, Razorpay) |
| `500` | `Exception` (catch-all) | Unexpected server errors |

---

## Design Principles

1. **Keep it simple** — Monolithic architecture, no microservices, no event sourcing, no Kafka
2. **Source of truth** — This backend is authoritative for all user, balance, and payment data
3. **Complete audit trail** — Every balance change creates a Transaction record
4. **Security first** — API keys hashed (SHA-256), passwords hashed (BCrypt), JWT signed, Razorpay signatures verified
5. **Constant-time comparisons** — For both API key verification and password checking
6. **Prevent enumeration** — Login errors are intentionally vague; forgot-password always returns success
7. **Pessimistic locking** — Wallet debit operations use DB-level write locks to prevent race conditions
8. **Async for side effects** — Email sending is `@Async` to avoid blocking API responses
9. **Fail gracefully** — Comprehensive exception handler maps errors to appropriate HTTP status codes
10. **Separation of concerns** — This backend does NOT call DeepSeek; a separate Python Gateway handles AI proxying

---

## Related Services

| Service | Responsibility | Communication |
|---|---|---|
| **Python Gateway** | Proxies requests to DeepSeek, verifies keys via `/api/v1/keys/verify`, reports usage via `/internal/usage/charge` | HTTP REST |
| **Frontend (SPA)** | User dashboard, key management, wallet recharge UI | HTTP REST (JWT auth) |
| **PostgreSQL** | Primary data store | JDBC |
| **Razorpay** | Payment processing | REST API (server-side) |
| **Google OAuth** | Social login ID token verification | REST API (tokeninfo) |
| **SMTP (Gmail)** | Transactional emails | SMTP |

---

*Built with Spring Boot 3, Java 21, and PostgreSQL.*
