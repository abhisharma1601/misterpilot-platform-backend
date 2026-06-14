# SCHEMA.md

## Project

platform.misterpilot.online

Backend service for MisterPilot.

Responsibilities:

* User Management
* API Key Management
* Wallet Management
* Payment Tracking
* Usage Billing
* Dashboard APIs

Technology Stack:

* Spring Boot 3
* Java 21
* PostgreSQL
* Spring Data JPA
* Flyway
* Caffeine Cache
* Google OAuth
* Easebuzz

---

# Database Schema

## User

Represents a registered MisterPilot user.

### Table

users

### Columns

| Column     | Type         | Constraints     |
| ---------- | ------------ | --------------- |
| id         | BIGSERIAL    | PRIMARY KEY     |
| name       | VARCHAR(255) | NOT NULL        |
| email      | VARCHAR(255) | UNIQUE NOT NULL |
| created_at | TIMESTAMP    | NOT NULL        |

### Relationships

* One User has One Wallet
* One User has Many ApiKeys

---

## ApiKey

Represents a MisterPilot API key.

API keys are never stored in plain text.

Only hashed values are stored.

### Table

api_keys

### Columns

| Column       | Type         | Constraints     |
| ------------ | ------------ | --------------- |
| id           | BIGSERIAL    | PRIMARY KEY     |
| hash_value   | VARCHAR(255) | UNIQUE NOT NULL |
| user_id      | BIGINT       | FK users(id)    |
| active       | BOOLEAN      | NOT NULL        |
| last_used_at | TIMESTAMP    | NULL            |
| created_at   | TIMESTAMP    | NOT NULL        |

### Relationships

* Many ApiKeys belong to One User

### Notes

Generated API Key Format:

mp_sk_xxxxxxxxxxxxxxxxxxxxxxxxx

Store:

SHA256(apiKey)

Never store plain text key.

---

## Wallet

Represents the current user balance.

### Table

wallets

### Columns

| Column     | Type          | Constraints         |
| ---------- | ------------- | ------------------- |
| id         | BIGSERIAL     | PRIMARY KEY         |
| user_id    | BIGINT        | UNIQUE FK users(id) |
| balance    | DECIMAL(12,2) | NOT NULL            |
| updated_at | TIMESTAMP     | NOT NULL            |

### Relationships

* One Wallet belongs to One User
* One Wallet has Many Transactions

### Notes

Balance is stored for fast reads.

Balance should never be negative.

---

## Transaction

Represents wallet activity.

### Table

transactions

### Columns

| Column       | Type          | Constraints    |
| ------------ | ------------- | -------------- |
| id           | BIGSERIAL     | PRIMARY KEY    |
| wallet_id    | BIGINT        | FK wallets(id) |
| type         | VARCHAR(50)   | NOT NULL       |
| status       | VARCHAR(50)   | NOT NULL       |
| amount       | DECIMAL(12,2) | NOT NULL       |
| reference_id | VARCHAR(255)  | NULL           |
| created_at   | TIMESTAMP     | NOT NULL       |

### Relationships

* Many Transactions belong to One Wallet

---

## TokenUsage

Represents token consumption per API call, tracked model-wise.

### Table

token_usage

### Columns

| Column           | Type            | Constraints          |
| ---------------- | --------------- | -------------------- |
| id               | BIGSERIAL       | PRIMARY KEY          |
| api_key_id       | BIGINT          | FK api_keys(id)      |
| user_id          | BIGINT          | FK users(id)         |
| model            | VARCHAR(100)    | NOT NULL             |
| output_tokens    | BIGINT          | NOT NULL DEFAULT 0   |
| cache_hit_tokens | BIGINT          | NOT NULL DEFAULT 0   |
| cache_miss_tokens| BIGINT          | NOT NULL DEFAULT 0   |
| cost_usd         | DECIMAL(12,8)   | NOT NULL             |
| cost_inr         | DECIMAL(12,2)   | NOT NULL             |
| created_at       | TIMESTAMP       | NOT NULL             |

### Relationships

* Many TokenUsage records belong to One ApiKey
* Many TokenUsage records belong to One User

### Notes

Tracks token consumption for MisterPilot keys only. Used for analytics and billing transparency.

---

# Transaction Types

```java
public enum TransactionType {
    PAYMENT,
    AI_USAGE,
    PROMOTION,
    REFUND
}
```

---

# Transaction Status

```java
public enum TransactionStatus {
    PENDING,
    SUCCESS,
    FAILED
}
```

---

# Domain Rules

## User

* Email must be unique.
* User is created after successful Google OAuth login.

---

## ApiKey

* User may have multiple API keys.
* API keys can be revoked.
* Inactive keys cannot be used.
* Only hash values are stored.

---

## Wallet

* Every user has exactly one wallet.
* Wallet is created during user registration.
* Balance cannot become negative.

---

## Transactions

Every balance change must create a transaction record.

Examples:

### Recharge

```text
Type: PAYMENT
Amount: +100.00
Status: SUCCESS
Reference: EZB_12345
```

### AI Usage

```text
Type: AI_USAGE
Amount: -0.15
Status: SUCCESS
Reference: REQ_98765
```

### Promotion

```text
Type: PROMOTION
Amount: +50.00
Status: SUCCESS
Reference: WELCOME_BONUS
```

---

# Future Tables

Not required for MVP.

Potential additions:

## UsageLog

Stores detailed AI usage.

Columns:

* request_id
* user_id
* model
* input_tokens
* output_tokens
* cost
* created_at

Purpose:

Detailed cost analysis and support debugging.

Not required for initial release.

---

# JPA Relationships

User

```java
@OneToMany(mappedBy = "user")
List<ApiKey> apiKeys;

@OneToOne(mappedBy = "user")
Wallet wallet;
```

ApiKey

```java
@ManyToOne
@JoinColumn(name = "user_id")
User user;
```

Wallet

```java
@OneToOne
@JoinColumn(name = "user_id")
User user;

@OneToMany(mappedBy = "wallet")
List<Transaction> transactions;
```

Transaction

```java
@ManyToOne
@JoinColumn(name = "wallet_id")
Wallet wallet;
```

---

# Design Principles

* Keep schema simple.
* Avoid premature optimization.
* Prefer clarity over flexibility.
* Use transactions for all wallet updates.
* Maintain complete financial audit trail through transactions.
* User balance must always match transaction history.
* Backend is the source of truth.

```
```
