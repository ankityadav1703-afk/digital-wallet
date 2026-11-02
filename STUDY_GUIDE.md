# Study Guide: understand this project before an interview

Read in this order. Each step says what to look for and the question you should be able to answer.

## 1. Read the flow of one request (30 min)
Follow `POST /api/wallet/transfer` through the code:
`WalletController.transfer` -> `WalletService.transfer` -> `WalletRepository.findByIdForUpdate` -> `Wallet.debit/credit` -> `WalletTransactionRepository.save`.
Question: *Why does the controller contain no business logic?* (Controller = HTTP only, service = rules + transaction.)

## 2. Layers and DTOs (30 min)
Look at `dto/` and `entity/`. Question: *Why return DTOs instead of entities?*
(Entities are tied to the database and lazy loading; DTOs are a stable API contract and avoid exposing internals like `passwordHash`.)

## 3. @Transactional (45 min)
`WalletService` methods. Question: *What happens if `target.credit()` throws after `source.debit()`?*
(Whole transaction rolls back; nothing is saved. Spring rolls back on RuntimeException.)

## 4. Locking: the core of the project (1 hour)
`WalletRepository.findByIdForUpdate`, `Wallet.@Version`, `WalletService.transfer`, `ConcurrencyIntegrationTest`.
- **Lost update**: two threads read balance 100, both write 90. Pessimistic lock (`SELECT ... FOR UPDATE`) makes the second wait.
- **Optimistic locking** (`@Version`): UPDATE ... WHERE version=? fails if someone else changed it; you retry. Good when conflicts are rare.
- **Deadlock**: A->B locks A then B, B->A locks B then A, both wait forever. Fix: always lock the lower id first.
- Why `findIdByUsername` returns only an id: loading the entity before locking can leave a stale copy in Hibernate's persistence context.
Questions: *Pessimistic vs optimistic locking? How did you prevent deadlocks? How do you prove it works?* (The concurrency tests.)

## 5. Idempotency (30 min)
`Idempotency-Key` handling in `transfer` and the unique constraint on `WalletTransaction`.
Question: *The client times out and retries a transfer. How do you avoid charging twice?*
(Same key returns the original result; checked after locks; DB unique constraint as backstop.)

## 6. Spring Security + JWT (1 hour)
`SecurityConfig`, `JwtAuthFilter`, `JwtService`, `CustomUserDetailsService`.
Flow: login -> server signs a JWT -> client sends `Authorization: Bearer <token>` -> filter validates and fills the SecurityContext -> `hasRole("ADMIN")` rules decide access.
Questions: *Why is CSRF disabled? (stateless token API, no session cookie.) 401 vs 403? (401 = not authenticated, 403 = authenticated but not allowed.) Where is the password stored? (BCrypt hash only.)*

## 7. Exception handling (20 min)
`GlobalExceptionHandler`. Question: *How do you return consistent errors?* (`@RestControllerAdvice` maps exceptions to HTTP status + JSON.)

## 8. Testing strategy (45 min)
- `WalletServiceTest`: unit tests with Mockito mocks, fast, no Spring.
- `WalletApiIntegrationTest`: full app with H2 + MockMvc.
- `ConcurrencyIntegrationTest`: threads + latch to force a race.
Questions: *Unit vs integration test? Why mock repositories? What does `@SpringBootTest` do? Why use H2 in tests and PostgreSQL in production?*

## 9. Run it yourself (1 hour)
`docker compose up --build`, open Swagger UI, register two users, deposit, transfer, freeze a wallet as admin. Then break things on purpose (remove `@Transactional`, remove the lock) and watch the concurrency test fail. This is the best way to understand it.

## Likely interview questions with short answers

1. **Why BigDecimal for money?** Floating point cannot represent decimals exactly (0.1 + 0.2 != 0.3).
2. **What is the N+1 problem?** One query for a list plus one extra query per row for a lazy relation. Fix with fetch joins/entity graphs. (Here, wallets are loaded individually, and the user relation is lazy and only touched inside transactions.)
3. **Why `open-in-view=false`?** Keeps database sessions inside the service layer so lazy loading problems show up in development, not as hidden queries in the web layer.
4. **What does `@Transactional(readOnly = true)` do?** Hints no writes happen; lets Hibernate skip dirty checking and the driver optimise.
5. **Why constructor injection?** Immutable dependencies, easy to test (`@InjectMocks`), no reflection needed.
6. **Difference between `@Component`, `@Service`, `@Repository`?** All register beans; they document the role and `@Repository` adds exception translation.
7. **What would you change for production?** Flyway migrations, refresh tokens, rate limiting, Testcontainers with real PostgreSQL, metrics, and a reversal/refund flow.
8. **How would this scale to many servers?** Locking is in the database, so it already works across instances. For huge load you would partition by wallet, use queues, and keep a ledger with double-entry bookkeeping.

## Honest note for interviews
Say you built it as a learning project to practise transactions, locking and security, and be ready to explain every class. If you cannot explain something, study it before you list it on your resume.
