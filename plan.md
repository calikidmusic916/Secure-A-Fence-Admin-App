# Implementation Plan - Adding Unit Tests & Token Check

## Objectives
1. Add a unit test suite under `app/src/test/java/...` to test repository/model serialization and authentication behavior.
2. Update `LoginActivity.kt` to check if a valid token already exists in `SharedPreferences` on startup, skipping the login screen if already authenticated.

## Step-by-Step Tasks
1. **Configure Test Dependencies & Source Directory**
   - Create `app/src/test/java/com/example/secureafenceadministrator/` directory structure.
2. **Add Unit Tests**
   - Create `LoginTest.kt` or `ApiClientTest.kt` to test request/response structures and basic logic.
3. **Add Session Check in LoginActivity**
   - Check `token` in `SharedPreferences` inside `LoginActivity.onCreate()`. If present and valid, jump straight to `MainActivity`.
