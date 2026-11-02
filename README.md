# Digital Wallet API

A Spring Boot REST backend for a digital wallet: users register, deposit, withdraw and **transfer money
between wallets**. It focuses on the things that matter in real money systems: atomic transactions,
concurrency control, idempotent retries, JWT security with roles, and thorough automated tests.

## Tech stack

Java 17 · Spring Boot 3.3 · Spring Web · Spring Data JPA / Hibernate · Spring Security (JWT, role-based access) ·
PostgreSQL · JUnit 5 · Mockito · H2 (tests only) · Swagger/OpenAPI · Maven · Docker · GitHub Actions

## Features

- Register and log in; passwords hashed with BCrypt; stateless JWT authentication
- One wallet per user, created together with the user in one transaction
- Deposit, withdraw and transfer (BigDecimal money, never negative balances)
- Immutable transaction ledger with filtering by type and pagination
- `Idempotency-Key` header on transfers: a retried request never moves money twice
- Admin APIs: freeze/unfreeze a wallet, system report (ROLE_ADMIN only)
- Consistent JSON error responses from one global exception handler
- Swagger UI for trying the API in the browser

## Architecture

```
Controller  ->  Service (business rules, @Transactional)  ->  Repository (Spring Data JPA)  ->  PostgreSQL
   |                 |
  DTOs        Entities (Wallet holds the money rules: no overdraft, frozen check)
```

| Package      | Responsibility                                                       |
|--------------|----------------------------------------------------------------------|
| `controller` | HTTP only: validate input, call a service, return DTOs               |
| `service`    | Business logic and transaction boundaries                            |
| `repository` | Database access, including `SELECT ... FOR UPDATE` locking queries   |
| `entity`     | JPA entities: `User`, `Wallet`, `WalletTransaction`                  |
| `security`   | JWT creation/validation and the request filter                       |
| `exception`  | Custom exceptions and the global error handler                       |

## How money stays correct (the interesting part)

1. **Atomic transfers.** `WalletService.transfer` is `@Transactional`. If the debit succeeds but anything
   later fails, the whole transaction rolls back: no half-finished transfers.
2. **Pessimistic locking.** Wallet rows are read with `SELECT ... FOR UPDATE`, so two requests changing the
   same wallet queue up instead of overwriting each other (no "lost update").
3. **Deadlock prevention.** A transfer locks both wallets in ascending id order, so A->B and B->A running
   at the same time cannot wait on each other forever.
4. **Optimistic locking safety net.** `Wallet` has a `@Version` column; if a lost update ever slipped
   through, the commit fails with HTTP 409 instead of silently corrupting a balance.
5. **Idempotent transfers.** Send `Idempotency-Key: <unique value>`; a replay returns the original result.
   The key is checked after the locks are held, and a database unique constraint is the final backstop.
6. **Rules inside the entity.** `Wallet.debit` refuses to go below zero and `ensureActive` blocks frozen
   wallets, so no code path can bypass them.

## Run it

### Option A: Docker (easiest)

```bash
docker compose up --build
```

API on http://localhost:8080, Swagger UI on http://localhost:8080/swagger-ui.html

### Option B: local Java + PostgreSQL

```bash
# start a PostgreSQL database named "wallet" (user wallet / password wallet), then:
mvn spring-boot:run
```

Defaults can be overridden with environment variables: `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`,
`JWT_SECRET` (32+ characters), `ADMIN_USERNAME`, `ADMIN_PASSWORD`.

A first admin account is created at startup: `admin` / `ChangeMe@123` (change it for any real use).

## Run the tests

```bash
mvn test        # unit + integration tests (uses in-memory H2, no database needed)
```

| Test class                       | What it proves                                                              |
|----------------------------------|-----------------------------------------------------------------------------|
| `WalletEntityTest`               | Money rules inside the entity (no overdraft, frozen wallets)               |
| `WalletServiceTest` (Mockito)    | Deposit, withdraw, transfer, idempotency, lock ordering, error cases        |
| `JwtServiceTest`                 | Tokens validate; tampered, expired and foreign-signed tokens are rejected   |
| `WalletApiIntegrationTest`       | Full REST flows through real security and JPA: auth, money, history, admin  |
| `ConcurrencyIntegrationTest`     | Many threads at once: no lost money, no deadlock, no overdraft, replay-safe |

## API overview

| Method | Path                                  | Access | Description                                  |
|--------|---------------------------------------|--------|----------------------------------------------|
| POST   | `/api/auth/register`                  | public | Create user + wallet, returns JWT            |
| POST   | `/api/auth/login`                     | public | Returns JWT                                  |
| GET    | `/api/wallet`                         | user   | My wallet (id, balance, status)              |
| POST   | `/api/wallet/deposit`                 | user   | `{ "amount": 100.00, "description": "..." }` |
| POST   | `/api/wallet/withdraw`                | user   | `{ "amount": 20.00 }`                        |
| POST   | `/api/wallet/transfer`                | user   | `{ "targetWalletId": 2, "amount": 10.00 }` + optional `Idempotency-Key` header |
| GET    | `/api/wallet/transactions`            | user   | `?type=TRANSFER&page=0&size=20`              |
| GET    | `/api/admin/report`                   | admin  | Users, wallets, total balance, transactions  |
| GET    | `/api/admin/wallets/{id}`             | admin  | Inspect any wallet                           |
| POST   | `/api/admin/wallets/{id}/freeze`      | admin  | Block all money movement for a wallet        |
| POST   | `/api/admin/wallets/{id}/unfreeze`    | admin  | Re-enable a wallet                           |

Status codes: 400 validation/invalid request, 401 not logged in, 403 not allowed, 404 not found,
409 conflict (frozen wallet, duplicate, concurrent update), 422 insufficient funds.

### Quick demo with curl

```bash
# register two users
curl -s -X POST localhost:8080/api/auth/register -H 'Content-Type: application/json' \
  -d '{"username":"alice","email":"alice@example.com","password":"Password123"}'
curl -s -X POST localhost:8080/api/auth/register -H 'Content-Type: application/json' \
  -d '{"username":"bob","email":"bob@example.com","password":"Password123"}'

TOKEN=<token from alice's response>

curl -s -X POST localhost:8080/api/wallet/deposit -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' -d '{"amount":100.00}'

curl -s -X POST localhost:8080/api/wallet/transfer -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' -H 'Idempotency-Key: demo-1' \
  -d '{"targetWalletId":2,"amount":25.00,"description":"lunch"}'
```

## Possible improvements

Flyway migrations instead of `ddl-auto=update`, refresh tokens, rate limiting, Testcontainers with real
PostgreSQL in CI, an outbox/event stream for notifications, and a transaction reversal (refund) flow.
