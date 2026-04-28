# Pulser DS - Personal Finance Manager - The Lucky Budget
> PROG7311 POE Part 2  

The Lucky Budget is personal finance manager application that helps user manage funds, expenses, goals and financial information that can provide financial knowledge to users allowing them to achieve financial freedom.

---

## Table of Contents
 
- [Features](#features)
- [Tech Stack](#tech-stack)
- [Project Structure](#project-structure)
- [Getting Started](#getting-started)
- [CI / CD](#ci--cd)
- [Database](#database)
- [Permissions](#permissions)

---

## Features
 
| Screen | Description |
|---|---|
| **Login / Register** | Secure account creation and login with SHA-256 password hashing and strict password-strength rules (8+ chars, upper, lower, digit, special character) |
| **Dashboard** | Personalised greeting, spending category tiles, recent transactions, and a savings-goal snapshot |
| **Transactions** | Full transaction history with the ability to add new expense entries linked to a category and bank account |
| **Analytics** | Date-range filtered spending breakdown with a custom donut chart and per-account / per-category spending rows |
| **Category Totals** | Detailed view of totals grouped by category |
| **Bank Accounts** | Add and manage multiple bank accounts; view per-account spending |
| **Savings Goals** | Create savings goals with target amounts and deadlines; track progress and mark goals complete |
| **Settings** | Toggle app preferences ((Future Implementation) 2FA, overspend alerts, subscription reminders, transaction alerts, savings milestones, dark mode, currency, collabrative access, google login); manage profile and session |
| **Manage Categories** | Create and manage custom expense categories |
 
---
 
## Tech Stack
 
- **Language:** Kotlin
- **Min SDK:** 26 (Android 8.0 Oreo)
- **Target / Compile SDK:** 36
- **Architecture:** MVVM (ViewModel + LiveData + Repository pattern)
- **Database:** Room 2.7 with KSP code generation, schema export, and versioned migrations
- **UI:** XML layouts with ViewBinding, Material Design 3 components, custom `DonutChartView`
- **Concurrency:** Kotlin Coroutines + `lifecycleScope`
- **Build System:** Gradle with Kotlin DSL (`.gradle.kts`)
- **CI:** GitHub Actions
### Key Dependencies
 
```
androidx.room:room-ktx
androidx.lifecycle:lifecycle-viewmodel-ktx
org.jetbrains.kotlinx:kotlinx-coroutines-android
com.google.android.material:material
androidx.constraintlayout:constraintlayout
```
 
---
 
## Project Structure
 
```
app/src/main/java/com/example/pulserds_prog7313/
├── App.kt                          # Custom Application class (auto-logout lifecycle callbacks)
├── DonutChart.kt / DonutChartView.kt  # Custom spending chart
├── data/                           # Room entities and DAOs
│   ├── AppDatabase.kt
│   ├── User.kt / UserDao.kt
│   ├── Category.kt / CategoryDao.kt
│   ├── ExpenseEntry.kt / ExpenseEntryDao.kt
│   ├── BankAccount.kt / BankAccountDao.kt
│   └── SavingsGoal.kt / SavingsGoalDao.kt
├── repository/                     # Repository classes (data access layer)
|   ├── BankAccountRepository.kt
|   ├── CategoryRepository.kt
|   ├── ExpenseEntryRepository.kt
|   ├── SavingsGoalRepository.kt
|   └── UserRepository.kt
├── ui/                             # Activities
│   ├── MainActivity.kt             # Login screen
│   ├── CreateAccActivity.kt        # Registration screen
│   ├── DashBoardActivity.kt
│   ├── TransactionsActivity.kt / AddTransactionActivity.kt
│   ├── AnalyticsActivity.kt / CategoryTotalsActivity.kt
│   ├── AccountsActivity.kt / AddAccountActivity.kt
│   ├── SavingsGoalsActivity.kt / CreateSavingsGoalActivity.kt
│   ├── SettingsActivity.kt
│   ├── ManageCategoriesActivity.kt
|   ├── ManageSubscriptionsActivity.kt
|   ├── AddSubscriptionActivity.kt
|   ├── BaseActivity.kt
|   ├── ForgotPasswordActivity.kt
│   └── adapters/                   # RecyclerView adapters
|       ├── AccountSpendingAdapter.kt
|       ├── CategoryRowAdapter.kt
|       ├── CategoryTileAdapter.kt
|       ├── DashSavingsAdapter.kt
|       ├── SpendingRowAdapter.kt
|       └── TransactionAdapter.kt
├── utils/
│   ├── SessionManager.kt           # SharedPreferences-based session handling
|   └── SingleLiveEvent.kt
└── viewmodel/                      # ViewModels and ViewModelFactory
|   ├── BankAccountViewModel.kt
|   ├── CategoryViewModel.kt
|   ├── ExpenseEntryViewModel.kt
|   ├── SavingsGoalViewModel.kt
|   ├── UserViewModel.kt
|   └── ViewModelFactory.kt

```
 
---
 
## Getting Started
 
### Prerequisites
 
- Android Studio Ladybug or newer
- JDK 17
- Android SDK with API 36 installed
### Clone and Open
 
```bash
git clone <repository-url>
cd prog7313-part1-g2-pulser-ds-main
```
 
Open the project in Android Studio by selecting **File → Open** and choosing the project folder. Android Studio will sync Gradle automatically.
 
### Build and Run
 
1. Connect a physical device or start an emulator running API 26+.
2. Click **Run ▶** in Android Studio, or run from the terminal:
```bash
./gradlew assembleDebug
```
 
The debug APK will be output to `app/build/outputs/apk/debug/app-debug.apk`.
 
---
 
## CI / CD
 
The project uses a **GitHub Actions** workflow (`.github/workflows/ci.yml`) that triggers on every push and pull request to `main`. The pipeline:
 
1. Checks out the repository and sets up JDK 17
2. Verifies the Gradle wrapper checksum for security
3. Builds the debug APK (`assembleDebug`)
4. Validates the Room database schema (`kspDebugKotlin`)
5. Runs JUnit unit tests (`testDebugUnitTest`)
6. Runs Android Lint (`lintDebug`)
7. Uploads the lint report, test results, and debug APK as build artifacts
---
 
## Database
 
Pulser DS uses a local **Room** database (`pulser_db`) at schema version 9. The database is a singleton accessed via `AppDatabase.getDatabase(context)`.
 
### Entities
 
| Entity | Description |
|---|---|
| `User` | Registered user accounts (email + SHA-256 password hash) |
| `Category` | User-defined expense categories |
| `ExpenseEntry` | Individual transaction records linked to a category and bank account |
| `BankAccount` | Bank accounts with balance tracking |
| `SavingsGoal` | Savings targets with progress tracking |
 
Schema JSON exports are stored in `app/schemas/` for migration history.
 
---
 
## Permissions
 
| Permission | Reason |
|---|---|
| `CAMERA` | Attach receipt photos to transactions |
| `READ_MEDIA_IMAGES` (API 33+) | Select images from gallery |
| `READ_EXTERNAL_STORAGE` (API ≤ 32) | Select images from gallery on older devices |
 
Camera hardware is declared as **not required** (`android:required="false"`), so the app installs on devices without a camera.

---

## References

[1] Google, "Save data in a local database using Room," *Android Developers*, 2024.
    [Online]. Available: https://developer.android.com/training/data-storage/room.
    [Accessed: 28 Apr. 2026].

[2] Google, "ViewModel overview," *Android Developers*, 2024.
    [Online]. Available: https://developer.android.com/topic/libraries/architecture/viewmodel.
    [Accessed: 28 Apr. 2026].
 
[3] Google, "LiveData overview," *Android Developers*, 2024.
    [Online]. Available: https://developer.android.com/topic/libraries/architecture/livedata.
    [Accessed: 28 Apr. 2026].
 
[4] JetBrains, "Coroutines guide," *Kotlin Documentation*, 2024.
    [Online]. Available: https://kotlinlang.org/docs/coroutines-guide.html.
    [Accessed: 28 Apr. 2026].
 
[5] Google, "Guide to app architecture," *Android Developers*, 2024.
    [Online]. Available: https://developer.android.com/topic/architecture.
    [Accessed: 28 Apr. 2026].
 
[6] Google, *AndroidX Room*, ver. 2.7.0-alpha11. [Software].
    Android Developers, 2024.
    Available: https://developer.android.com/jetpack/androidx/releases/room.
 
[7] Google, *AndroidX Lifecycle ViewModel KTX*, ver. 2.8.4. [Software].
    Android Developers, 2024.
    Available: https://developer.android.com/jetpack/androidx/releases/lifecycle.
 
[8] JetBrains, *kotlinx-coroutines-android*, ver. 1.8.1. [Software].
    JetBrains, 2024.
    Available: https://github.com/Kotlin/kotlinx.coroutines.
 
[9] Google, *AndroidX Core KTX*, ver. 1.13.1. [Software].
    Android Developers, 2024.
    Available: https://developer.android.com/jetpack/androidx/releases/core.
 
[10] Google, *AndroidX AppCompat*, ver. 1.7.0. [Software].
     Android Developers, 2024.
     Available: https://developer.android.com/jetpack/androidx/releases/appcompat.
 
[11] Google, *Material Design Components for Android*, ver. 1.12.0. [Software].
     Google, 2024.
     Available: https://github.com/material-components/material-components-android.
 
[12] Google, *AndroidX ConstraintLayout*, ver. 2.2.0. [Software].
     Android Developers, 2024.
     Available: https://developer.android.com/jetpack/androidx/releases/constraintlayout.
 
[13] Google, *AndroidX Activity KTX*, ver. 1.9.0. [Software].
     Android Developers, 2024.
     Available: https://developer.android.com/jetpack/androidx/releases/activity.
 
[14] Google, "KSP quickstart," *Android Developers*, 2024.
     [Online]. Available: https://developer.android.com/build/migrate-to-ksp.
     [Accessed: 28 Apr. 2026].
 
[15] Google, "View Binding," *Android Developers*, 2024.
     [Online]. Available: https://developer.android.com/topic/libraries/view-binding.
     [Accessed: 28 Apr. 2026].
 
[16] Google, "Requesting permissions at runtime," *Android Developers*, 2024.
     [Online]. Available: https://developer.android.com/training/permissions/requesting.
     [Accessed: 28 Apr. 2026].
 
[17] Google, "Share files with FileProvider," *Android Developers*, 2024.
     [Online]. Available: https://developer.android.com/reference/androidx/core/content/FileProvider.
     [Accessed: 28 Apr. 2026].
 
[18] Google, "SharedPreferences — save key-value data," *Android Developers*, 2024.
     [Online]. Available: https://developer.android.com/training/data-storage/shared-preferences.
     [Accessed: 28 Apr. 2026].
 
[19] Gradle Inc., "Gradle Kotlin DSL primer," *Gradle Documentation*, 2024.
     [Online]. Available: https://docs.gradle.org/current/userguide/kotlin_dsl.html.
     [Accessed: 28 Apr. 2026].
 
[20] Google, "Canvas and drawables," *Android Developers*, 2024.
     [Online]. Available: https://developer.android.com/develop/ui/views/graphics/drawables.
     [Accessed: 28 Apr. 2026].
 
[21] GitHub, "GitHub Actions documentation," *GitHub Docs*, 2024.
     [Online]. Available: https://docs.github.com/en/actions.
     [Accessed: 28 Apr. 2026].
 
[22] JUnit Team, *JUnit*, ver. 4.13.2. [Software].
     JUnit, 2021.
     Available: https://junit.org/junit4.
 
[23] Google, *AndroidX Test Espresso Core*, ver. 3.6.1. [Software].
     Android Developers, 2024.
     Available: https://developer.android.com/training/testing/espresso.
 
