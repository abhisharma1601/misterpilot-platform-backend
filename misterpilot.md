# PLATFORM.MISTERPILOT.ONLINE - BACKEND CONTEXT

## Purpose

This project is the backend service for platform.misterpilot.online.

The backend is responsible for:

* User Management
* Google OAuth Authentication
* API Key Management
* Wallet Management
* Usage Tracking
* Payment Processing
* Dashboard APIs

This backend DOES NOT communicate with DeepSeek directly.

DeepSeek communication is handled by the separate Python Gateway service.

This backend is the source of truth for users, balances, payments, and usage.

---

# Technology Stack

Java: 21

Framework: Spring Boot 3

Database: PostgreSQL

Cache: Caffeine

Authentication: Google OAuth2

Authorization: HTTP_AUTH_EAC4

Payment Gateway: Easebuzz

Secrets: AWS Secrets Manager

Build Tool: Maven

ORM: Spring Data JPA

Validation: Jakarta Validation

Migration: Flyway

---

# Architecture Style

Monolithic Architecture

Do NOT use:

* Microservices
* Kafka
* Event Sourcing
* CQRS

Keep implementation simple and maintainable.

---

# Core Domains

## User

Represents a registered user.

Fields:

* id
* email
* googleId
* fullName
* profilePicture
* active
* createdAt
* updatedAt

---

## Wallet

Represents user's available balance.

Fields:

* id
* userId
* balanceInr
* createdAt
* updatedAt

Rules:

* Balance can never be negative.
* All deductions must be transactional.

---

## ApiKey

Represents a MisterPilot API Key.

Format:

mp_sk_xxxxxxxxxxxxxxxxxxxxx

Store only hashed values.

Never store plain text API keys.

Fields:

* id
* userId
* keyHash
* name
* active
* lastUsedAt
* createdAt

---

## Payment

Represents a recharge transaction.

Fields:

* id
* userId
* amountInr
* status
* transactionId
* gatewayResponse
* createdAt

Statuses:

* PENDING
* SUCCESS
* FAILED

---

# Implementation Log — 2025-07-11

## Folder Structure

```
platform/
├── src/main/java/online/misterpilot/platform/
│   ├── auth/          — AuthController
│   ├── config/        — GoogleProperties (@ConfigurationProperties)
│   ├── controller/    — KeyController
│   ├── dto/
│   │   ├── request/   — GoogleLoginRequest, LoginRequest, RegisterRequest,
│   │   │                VerifyKeyRequest, DisableKeyRequest, TransactionRequest
│   │   └── response/  — LoginResponse, GenerateKeyResponse, VerifyKeyResponse,
│   │                    DisableKeyResponse, TransactionResponse
│   ├── entity/        — User, Wallet, ApiKey, Transaction, TransactionType, TransactionStatus
│   ├── repository/    — UserRepository, WalletRepository, ApiKeyRepository, TransactionRepository
│   ├── service/       — AuthService, GoogleService, ApiKeyService, WalletService
│   ├── util/          — AuthUtil (JWT + BCrypt), KeyUtil (API key hash + verify)
│   └── PlatformApplication.java
```

## Entity Changes (vs original schema.md)

| Entity | Change |
|---|---|
| User | Added `passwordHash` (nullable, for email/password users). Added `googleId` (nullable, for Google OAuth users). A user can have both later after linking. |
| Wallet | balance uses `BigDecimal` with `precision=12, scale=2`. `@OneToOne` with User. Cascade ALL. |
| ApiKey | `hashValue` is SHA-256 hex (deterministic, so `findByHashValue` works). `active` flag. `@ManyToOne` User. |
| Transaction | New entity (replaces Payment). `@ManyToOne` Wallet. Fields: type (RECHARGE/USAGE_CHARGE/REFUND/ADJUSTMENT), status (PENDING/SUCCESS/FAILED), amount, referenceId, createdAt. |
| TransactionType enum | RECHARGE, USAGE_CHARGE, REFUND, ADJUSTMENT |
| TransactionStatus enum | PENDING, SUCCESS, FAILED |

## Dependencies Added to pom.xml

```xml
spring-boot-starter-security     ← Security filter chain, BCryptPasswordEncoder
spring-boot-starter-oauth2-client ← Google OAuth2 client support
jjwt-api 0.12.6                  ← JWT creation & parsing
jjwt-impl 0.12.6 (runtime)       ← JWT implementation
jjwt-jackson 0.12.6 (runtime)    ← JWT JSON serialization
```

## Application Properties

### application.properties
```
spring.application.name=platform
server.port=8080
spring.profiles.active=dev
```

### application-dev.properties
```
spring.datasource.url=jdbc:postgresql://localhost:5432/misterpilot_platform
spring.jpa.hibernate.ddl-auto=validate
spring.flyway.enabled=true
google.client-id=<from Google Cloud Console>
google.client-secret=<from Google Cloud Console>
app.auth.jwt-secret=<min 32 chars, HMAC-SHA key>
```

## AuthUtil (src/main/java/.../util/AuthUtil.java)

| Method | Purpose |
|---|---|
| `generateJwt(User)` | Signs JWT with HMAC-SHA. Claims: sub=userId, email, name, googleId. Expiry: 7 days. |
| `hashPassword(String)` | BCrypt hash via Spring Security's BCryptPasswordEncoder |
| `verifyPassword(String, String)` | Constant-time BCrypt comparison |

## KeyUtil (src/main/java/.../util/KeyUtil.java)

| Method | Purpose |
|---|---|
| `generateApiKey()` | Returns `mp_sk_` + 48 random hex chars (SecureRandom) |
| `hashApiKey(String)` | SHA-256 hex (deterministic — enables DB lookup via `findByHashValue`) |
| `verifyApiKey(String, String)` | Constant-time comparison via `MessageDigest.isEqual()` |

### Why SHA-256 for API keys (not BCrypt)?
BCrypt has a random salt per hash → cannot do `findByHashValue()` lookup.
SHA-256 is deterministic → same plain key always produces same hash → lookup works.
API keys have 48 bytes of random entropy (~384 bits) → brute-force is infeasible.

## Auth Endpoints (/api/auth)

### POST /api/auth/register
Request: `{ name, email, password }`
Flow: `AuthService.register()` → validate email unique → hash password → save User → create Wallet (balance=0) → generate JWT → return LoginResponse

### POST /api/auth/login
Request: `{ email, password }`
Flow: `AuthService.login()` → findByEmail → check passwordHash exists (Google users can't use this) → verifyPassword → generate JWT → return LoginResponse

Error messages use "Invalid email or password" for both not-found and wrong-password to prevent email enumeration.
Google users trying password login get a specific message: "This account uses Google sign-in."

### POST /api/auth/google/login
Request: `{ idToken }`
Flow: `GoogleService.authenticate()` → verifyGoogleToken() → findByGoogleId →

  - **Found**: generate JWT → return LoginResponse
  - **Not found**: call `AuthService.registerGoogleUser(googleId, name, email)` →
      - If email already exists (email/password user who never used Google): **link** googleId to existing user → return LoginResponse
      - If brand new: create User + Wallet → return LoginResponse

### Google Token Verification (Security)
Two checks in `verifyGoogleToken()`:
1. `sub` claim present → token is valid Google-issued
2. `aud` claim == `googleProperties.getClientId()` → token was minted for OUR OAuth app, not someone else's

Without the `aud` check, any valid Google ID token from any OAuth app would be accepted.

## Key Endpoints (/api/keys)

### POST /api/keys/generate (authenticated)
Generates a new API key. Returns plain key ONCE. Only SHA-256 hash stored in DB.

### POST /api/keys/verify (NO authentication — service-to-service)
Called by Python Gateway before proxying to DeepSeek.
Three checks:
1. Key exists (findByHashValue)
2. Key is active
3. Owner has wallet balance ≥ ₹5.00

All failures return same message to prevent information leakage.

### POST /api/keys/disable (authenticated)
Deactivates a key. Verifies ownership (apiKey.user.id == authenticated user id).

## Wallet Endpoints (/api/wallet)

### POST /api/wallet/transaction (authenticated)
Request: `{ amount: BigDecimal, type: TransactionType }`
Flow: `WalletService.processTransaction()` →

| Type | Action |
|---|---|
| RECHARGE | balance += amount |
| USAGE_CHARGE | balance -= amount (fails if insufficient) |
| REFUND | balance += amount |
| ADJUSTMENT | NOT YET IMPLEMENTED (admin-only, throws UnsupportedOperationException) |

Rules:
- Amount must be > 0
- Wallet balance can never go negative
- Wallet update + Transaction insert are @Transactional (atomic)
- Amount stored as positive — direction is in the TransactionType

## DTO Log-Safety

All DTOs containing sensitive values (API keys, passwords) override `toString()` to mask the value:
- `VerifyKeyRequest.toString()` → `"VerifyKeyRequest{apiKey=***}"`
- `DisableKeyRequest.toString()` → `"DisableKeyRequest{apiKey=***}"`
- `User.passwordHash` annotated `@JsonIgnore` + `@ToString.Exclude`

## Security Decisions Summary

| Decision | Why |
|---|---|
| JWT signing via HMAC-SHA | Shared secret with Python Gateway. AWS Secrets Manager in prod. |
| BCrypt for passwords | Industry standard, ships with spring-security |
| SHA-256 for API keys | Deterministic → DB lookup. 384-bit random entropy → no brute-force. |
| Constant-time comparison for keys | `MessageDigest.isEqual()` — no timing side-channel |
| verifyKey returns same error for all failures | Prevents probing (can't tell if key doesn't exist or is disabled or low balance) |
| Google aud claim validated | Prevents cross-app token replay |
| Wallet + Transaction in same @Transactional | Atomic — balance and history can never diverge |

## TODO / Not Yet Implemented

- [ ] JWT Auth Filter (OncePerRequestFilter) — populate SecurityContext so getAuthenticatedUser() works
- [ ] Flyway V1__init.sql migration
- [ ] Email/password login wired in controller (service method exists)
- [ ] ADJUSTMENT transaction type (admin-only)
- [ ] Easebuzz payment gateway integration
- [ ] AWS Secrets Manager for JWT secret in production
- [ ] SecurityConfig (disable form login, allow /api/auth/** and /api/keys/verify publicly)
