# Architecture Overview

## 1. Project Structure

This section provides a high-level overview of the project's directory and file structure, categorised by architectural layer or major functional area. It is essential for quickly navigating the codebase, locating relevant files, and understanding the overall organization and separation of concerns.

```#!/bin/bash
[Project Root]/
├── app/                      # Main Android application (UI/Compose, ViewModel, UI logic)
│   ├── src/
│   │   ├── main/
│   │   │   ├── androidManifest.xml
│   │   │   ├── java/com/example/app/   # UI components, ViewModels, Activities
│   │   │   ├── res/                 # Layouts, Drawables, etc.
│   │   └── build.gradle.kts       # Module-specific build configuration
│
├── domain/                   # Core logic, business models, and interfaces
│   ├── src/
│   │   ├── main/
│   │   │   ├── kotlin/com/example/domain/
│   │   │   │   ├── types/           # Entities, value classes
│   │   │   │   ├── interfaces/      # Interfaces for business logic
│   │   │   └── build.gradle.kts
│   └── build.gradle.kts       # Module-specific build configuration
│
├── pipeline_core/             # Image processing orchestration logic (core backend)
│   ├── src/
│   │   ├── main/
│   │   │   ├── kotlin/com/example/pipeline/
│   │   │   └── build.gradle.kts
│
├── imaging_opencv_android/    # Adapter for OpenCV image processing on Android
│   ├── src/
│   │   ├── main/
│   │   │   ├── kotlin/com/example/imaging/
│   │   │   └── build.gradle.kts
│
├── ocr_core/                         # Contracts OCR (pure Kotlin)
│   ├── src/
│   │   ├── main/
│   │   │   ├── kotlin/com/example/ocr/core/
│   │   │   │   ├── api/                     # OcrEngine, OcrResult, OcrPolicy, TextLayer models
│   │   │   └── build.gradle.kts
│   │   └── build.gradle.kts                 # Cấu hình module ocr-core
│
├── ocr_mlkit_android/                # OCR on-device (engine A)
│   ├── src/
│   │   ├── main/
│   │   │   ├── kotlin/com/example/ocr/mlkit/
│   │   │   └── build.gradle.kts
│   │   └── build.gradle.kts                 # Cấu hình module ocr-mlkit-android
│
├── ocr_tesseract_android/            # (Tuỳ chọn) OCR on-device (engine B – Tesseract)
│   ├── src/
│   │   ├── main/
│   │   │   ├── kotlin/com/example/ocr/tesseract/
│   │   │   └── build.gradle.kts
│   │   └── build.gradle.kts                 # Cấu hình module ocr-tesseract-android
│
├── ocr_remote/                       # (Tuỳ chọn) Client gọi OCR cloud (HTTP)
│   ├── src/
│   │   ├── main/
│   │   │   ├── kotlin/com/example/ocr/remote/
│   │   │   └── build.gradle.kts
│   │   └── build.gradle.kts                 # Cấu hình module ocr-remote
│
├── exporter_pdf_android/      # PDF export functionality for processed images
│   ├── src/
│   │   ├── main/
│   │   │   ├── kotlin/com/example/exporter/pdf/
│   │   │   └── build.gradle.kts
├── data_store_android/        # Local data storage with Room and file system management
│   ├── src/
│   │   ├── main/
│   │   │   ├── kotlin/com/example/datastore/
│   │   │   └── build.gradle.kts
│
├── docs/                      # Project documentation (e.g., API docs, setup guides)
├── scripts/                   # Automation scripts (e.g., deployment, data seeding)
├── .github/                   # GitHub Actions or other CI/CD configurations
├── .gitignore                 # Specifies intentionally untracked files to ignore
├── README.md                  # Project overview and quick start guide
└── ARCHITECTURE.md            # This document
```

## 2. High-Level System Diagram

The following diagram illustrates the key components of the system and how they interact:

```#!/bin/bash
[User]
  ▼
[ app (UI) ] ──► [ domain (use-cases) ] ──► [ pipeline_core ]
    │                 │
    │                 └────────► [ imaging_opencv_android ]
    │
    ├────────► [ ocr_core ] ──► ( ocr_mlkit_android | ocr_tesseract_android | ocr_remote? )
    ├────────► [ exporter_pdf_android ]
    └────────► [ data_store_android ]  (Room + PageStore files)

```

This system consists of:

* **Frontend**: User-facing components that communicate with the backend services.
* **Backend Services**: APIs that handle business logic, data processing, and communication with external APIs and databases.
* **Database**: Stores application data, including processed images and metadata.
* **External Integrations**: Integration with third-party services as needed.

## 3. Core Components

### 3.1. App (UI/Compose)

* **Description**: Camera capture, review, crop, filters, export, share. Triggers pipeline and OCR via domain use-cases.

* **Tech**: Kotlin, Jetpack Compose, CameraX, Lifecycle, WorkManager (optional for background).

* **Deployment**: Google Play (Android).

### 3.2. On-device Processing

#### 3.2.1. pipeline_core

* **Role**: Orchestrates steps (detect/deskew, perspective warp, denoise, CLAHE, orientation policy, etc.). Pure Kotlin, platform-agnostic.

* **Note**: Owns sequencing & policies; no Android/OpenCV types leak into domain.

#### 3.2.2. imaging_opencv_android

* **Role**: Bridges Android Bitmap/YUV to OpenCV operations used by pipeline (warp, deskew, threshold, CLAHE…).

* **Note**: Android-specific; keeps OpenCV out of domain/pipeline_core public APIs.

#### 3.2.3. OCR Engines

* **ocr_core**: Contracts OcrEngine, OcrPolicy, OcrResult, TextLayer.

* **ocr_mlkit_android**: On-device engine A (fast setup, multi-lang).

* **ocr_tesseract_android**: On-device engine B (fine-tuning, custom langs). Optional.

* **ocr_remote**: Thin client to call cloud OCR. Optional/off by default.

#### 3.2.4. exporter_pdf_android

* **Role**: Renders processed pages → paginated PDF (A4/Letter presets, DPI control, metadata).

### 3.3. Local Persistence

* **Module**: data_store_android

* **Room**: entities for documents, pages, processing states, OCR text layers.

* **Files**: PageStore for originals/intermediates/outputs with stable URIs.

## 4. Data Stores

### 4.1. Local Database

* **Name**: Room Database

* **Type**: Room (SQLite)

* **Purpose**: Metadata & state tracking.

* **Suggested tables:**

* **Purpose**: Stores metadata for documents, pages, and scan processing status.

* **Key Schemas**:

    * `documents(id, title, createdAt, updatedAt, pageCount, colorMode, exportProfile)`
    * `pages(id, documentId, index, sourceUri, processedUri, orientation, status)`
    * `ocr_text(pageId, blockIndex, text, bbox, lang, confidence)`

### 4.2. File Storage

* **Name**: File Storage

* **Type**: Android File System

* **Purpose**: Stores processed image files locally before exporting them to PDF.

* **Layout:**

    * `/documents/{docId}/raw/{pageIndex}.jpg`
    * `/documents/{docId}/proc/{pageIndex}.png`
    * `/documents/{docId}/pdf/{docId}.pdf`

* Notes: Keep content-addressed temp cache for pipeline intermediates if needed.

## 5. External Integrations / APIs

This app is fully functional **offline**. All integrations below are **optional** and must be explicitly enabled via feature flags and runtime settings.

### 5.1 Remote OCR (optional)

* **Purpose:** Send page images to a cloud OCR for languages/layouts not well covered on-device.
* **Module:** `ocr_remote` (thin HTTPS client behind `ocr_core` contracts).
* **Integration Method:** HTTP(S) JSON API (provider-agnostic); inject an `OcrEngine` implementation at runtime.
* **Security:** TLS; auth via API key stored in Android Keystore and surfaced through encrypted SharedPreferences.
* **Privacy:** Never uploads without explicit user action/consent; redact EXIF and strip PII headers.

### 5.2 Cloud Backup/Sync (optional, future)

* **Purpose:** User-opt-in backup/sync of documents and PDFs.
* **Status:** Not enabled in v1; behind a feature flag.
* **Integration Options (examples):** Google Drive, Firebase Storage, WebDAV, or vendor SDKs—chosen per ADR when/if implemented.
* **Notes:** Background jobs via WorkManager with metered-network and battery constraints; resumable uploads.

### 5.3 Android Shares & Intents

* **Purpose:** Share exported PDFs/images to other apps (email, chat, Drive…) using system share sheet.
* **Method:** `FileProvider` URIs with explicit MIME types; no vendor lock-in.

### 5.4 Configuration & Feature Flags

* `FeatureFlags.remoteOcr` → toggles `ocr_remote`.
* `FeatureFlags.cloudSync` → hides/shows backup UI and background jobs.
* Provider details (endpoints/keys) are stored in encrypted prefs; never hard-code secrets.

### 5.5 Failure & Fallback Behavior

* If remote OCR fails → gracefully fall back to on-device engines (ML Kit / Tesseract).
* If backup/sync is disabled/unavailable → continue local-only; no blocking dialogs.

## 6. Deployment & Infrastructure

* **Platform:** Android (offline-first, no server required).
* **Packaging:** Android App Bundle (.aab) for Play Store.
* **Signing:** Use Play App Signing for releases (Android’s recommended way).
* **Versioning:** Increase versionCode each release; set a readable versionName (e.g., 1.0.0).
* **Build variants:** debug for development; release for publishing (uses R8 shrinker by default).
* **Release flow:** Build → (optionally test) → upload .aab to Play Internal track → verify → promote to Production.
* **CI/CD (optional):** Can automate builds/tests later; not required for this project.
* **Monitoring (optional):** Logcat locally; add a crash tool later only if needed.

## 7. Security & Privacy (Offline-first)

The app works fully **on-device**. Nothing leaves the device unless the user explicitly opts in (e.g., Remote OCR or backup/sync).

### 7.1 Data Minimization & Privacy

* No tracking/analytics by default.
* Strip EXIF and other metadata from processed images/PDFs.
* Let users delete documents/pages/exports; support “Delete all data” within app.

### 7.2 Authentication & Local Access

* No account system by default.
* Optional app lock: BiometricPrompt (fallback to device credential or app PIN).
* Idle-timeout (optional) re-prompts for biometric/PIN.

### 7.3 Storage at Rest (Encryption)

* **Images/PDFs**: store under app-private storage; use Jetpack Security `EncryptedFile` (AES-GCM; keys in Android Keystore).
* **Room DB** (metadata, OCR text): use SQLCipher or Room’s `SupportFactory` for encryption.
* Secrets (API keys/endpoints for optional features): EncryptedSharedPreferences + Keystore.

**EncryptedFile sketch (Kotlin):**

```kotlin
val masterKey = MasterKey.Builder(context)
  .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
  .build()

val encFile = EncryptedFile.Builder(
  context,
  targetFile,
  masterKey,
  EncryptedFile.FileEncryptionScheme.AES256_GCM_HKDF_4KB
).build()

encFile.openFileOutput().use { it.write(bytes) }
```

## 7.4 Network Security (only if a network feature is enabled)

* HTTPS/TLS only. Block cleartext via Network Security Config.

* OkHttp with reasonable timeouts; exponential backoff.

* Optional certificate pinning for Remote OCR/backup providers.

* Do not log payloads or credentials. Redact error messages.

## 7.5 Permissions & Media Access

* Use CameraX for capture.

* Prefer Photo Picker for imports (no broad storage permissions).

* If export to public media is offered, use scoped MediaStore APIs; never request legacy storage.

## 7.6 Temporary Files, Caches & Logs

* Write intermediates to cache/; purge on success or on app start.

* Disable verbose logs in release; guard logs behind BuildConfig.DEBUG.

* Never log PII/OCR text.

## 7.7 Secure Sharing

* Share via FileProvider URIs + FLAG_GRANT_READ_URI_PERMISSION.

* Revoke grants after share (or use one-time ephemeral URIs).

* Sanitize filenames; ensure correct MIME types.

## 7.8 Backups & Auto-Backup

* Exclude transient paths (cache/intermediates) in fullBackupContent.

* If enabling cloud backup/sync (see Section 5), require explicit user opt-in and show network/privacy warnings.

## 7.9 App Integrity (optional)

* Play Integrity API check (basic verdict) before enabling network features that handle sensitive docs.

## 7.10 Secure Coding & Hardening

* Input size limits for images/pages to avoid OOM.

* R8 shrink/obfuscate; remove unused code/resources.

* Validate intent inputs; use explicit intents internally.

* Run Lint/Detekt rules for WebView/file-scheme, exported components, and leaked permissions (build breaks on high severity).

## 7.11 Threat Model (scope & mitigations)

* Lost device → biometric/PIN lock; encrypted storage.

* Malicious apps → app-private storage; share via FileProvider only.

* MITM (if network used) → TLS + optional pinning; no cleartext.

* Over-sharing → explicit share sheet, sanitized files, revoke grants.

## 8. Development & Testing

### 8.1 Local Setup

* **Tools**: Android Studio (JDK 17), Android SDK/NDK (if OpenCV native), Gradle wrapper.
* **Clone & build**: `./gradlew assembleDebug`
* **Run checks**: `./gradlew ktlintCheck detekt testDebugUnitTest`

### 8.2 Test Strategy (pyramid)

1. **Unit (fast, majority)**
    * `pipeline_core`: step orchestration, policies (orientation, color mode), error handling.
    * `domain`: use-cases, mappers, result types.
    * `ocr_core`: engine contracts, adapters (no device/ML code).
2. **Component / Integration**
    * `imaging_opencv_android`: OpenCV ops against test images (instrumented or Robolectric with Bitmap).
    * OCR engines: verify **contract compliance** using small fixtures (skip heavy models in unit scope).
3. **UI / Instrumented**
    * Compose screens (review, crop, export) with Espresso/Compose UI Test.
    * Permission and share flows via `FileProvider` intents.
4. **Performance**
    * Macrobenchmark: page import → pipeline → export timings; startup time; memory usage.
5. **Golden / Snapshot (optional)**
    * Pixel-goldens for processed pages (tolerances for minor OpenCV drift).

### 8.3 Test Data & Fixtures

* Store under `testdata/` (not bundled with release):
    * `raw/` (original photos), `proc/` (expected), `ocr/` (expected text).
* Keep a **tiny** representative set (A4 portrait/landscape, receipt, low-light).
* Large or proprietary samples: keep out of VCS; fetch via private artifact on CI.

### 8.4 Example Unit Tests

pipeline_core – orientation policy

```kotlin
class OrientationPolicyTest {
  @Test fun `landscape input rotates to portrait when profile=A4`() {
    val policy = OrientationPolicy.A4PortraitDefault
    val result = policy.decide(width = 1920, height = 1080, aspect = 1920f/1080f)
    assertEquals(Rotation.Rotate90CW, result.rotation)
  }
}
```

domain – use case contracts

```kotlin
class RunPipelineUseCaseTest {
  @Test fun `returns processed page with metadata`() = runTest {
    val repo = FakePageRepo()
    val engine = FakePipelineEngine() // pure Kotlin
    val uc = RunPipelineUseCase(repo, engine)

    val out = uc(PageId("p1"))
    assertTrue(out.isSuccess)
    assertEquals(PageStatus.Processed, out.getOrThrow().status)
  }
}
```

## 8.5 Instrumented / Integration Examples

imaging_opencv_android – warp & CLAHE

```kotlin
@RunWith(AndroidJUnit4::class)
class ImagingIntegrationTest {
  @Test fun warp_then_clahe_matches_golden() {
    val src = loadBitmapFromAssets("testdata/raw/doc_landscape.jpg")
    val warped = OpenCvOps.warpPerspective(src, fourPoint)
    val enhanced = OpenCvOps.clahe(warped)
    assertBitmapAlmostEquals(
      loadBitmapFromAssets("testdata/proc/doc_landscape_expected.png"),
      enhanced,
      psnrThreshold = 35.0
    )
  }
}
```

## 8.6 Performance & Stability

* Macrobenchmark (separate module):
    * capture→process→export end-to-end time budget (e.g., ≤ 2.5s on mid-range device).
    * Startup warm (≤ 700ms), cold (≤ 1.5s) goals.
    * Baseline Profiles: generate & ship to reduce cold-start JIT cost.
    * ANR/Crash checks: long operations via Dispatchers.Default / WorkManager; no heavy work on main thread.

## 8.7 Static Analysis & Style

* **Ktlint:** formatting; fails CI on violations.
* **Detekt:** enforce forbidden Android leaks (exported components, file://, WebView).
* **Lint:** treat Fatal as errors; keep a small lint-baseline.xml and refresh when intentional.

## 8.8 Logging & Debugging

* BuildConfig.DEBUG gate for logs; no PII/OCR text in logs.
* Add a hidden Debug panel (long-press version): show pipeline steps, timings, and last error.

## 8.9 Feature Flags in Tests

* Flags (e.g., remoteOcr, cloudSync) are injectable.
* Default test config: all network features OFF; explicit tests enable them via DI.

## 8.10 CI Test Matrix (GitHub Actions)

* **Jobs:**
    * unit: ./gradlew testDebugUnitTest ktlintCheck detekt
    * instrumented (optional): headless AVD API 29/33 (smoke subset)
    * macrobenchmark (optional, nightly on physical farm if available)
    * Artifacts: test reports, coverage (Jacoco), .aab, mapping files.
    * Caching: Gradle cache + AVD snapshot (if used).

## 8.11 Accessibility & i18n (checks)

* TalkBack traversal of review/export screens.
* Minimum touch targets (48dp), contrast check, dynamic fonts.
* Strings externalized; right-to-left layout sanity test.

## 8.12 Release Readiness Checklist

* [ ] Unit + instrumented suites green
* [ ] Lint/Detekt clean (no high severity)
* [ ] Performance budgets met (macrobench)
* [ ] ProGuard mapping archived
* [ ] Manual smoke: capture→process→OCR→export→share

## 9. Roadmap (with Login & Share)

This plan keeps the app **offline-first** while adding **accounts** and **share-by-link** as opt-in features. Each phase has clear deliverables and acceptance criteria.

### 9.1 Login

**Goal:** Let users sign in to enable cloud features (share/sync) without changing offline UX.

* **Auth options (choose via ADR):**
    * **Option A (Supabase)**: Email OTP + Google; Postgres + Storage; RLS for per-user rows.
    * **Option B (Firebase)**: Google/Email; Storage; minimal Cloud Functions.
    * **Option C (Self-hosted)**: OIDC provider + S3-compatible storage.
* **Client flow:** Settings → “Sign in” → provider UI → token cached; app shows “Cloud features enabled”.
* **Storage of tokens:** Android Keystore (EncryptedSharedPreferences).
* **AC (acceptance criteria):**
    * Login/logout works; offline mode still fully works.
    * No PII in logs; tokens never leave secure storage except in HTTPS requests.

### 9.2 Share by Link

**Goal:** From any Document, create a **view link** that others can open in a web viewer. Owner can revoke or expire it.

* **Share model:**
    * **Unlisted link** with short ID; **optional password**; **expiry** (e.g., 7/30 days); **revoke**.
    * **Watermark** (toggle): “Scanned with {AppName}” + timestamp (on image or PDF).
    * **Permissions:** `view` only in v1 (download toggle on/off).
* **Upload path (when sharing):**
    1) Export pages → PDF (and optional page PNGs)
    2) Upload artifacts to cloud storage under `/u/{userId}/shares/{shareId}/...`
    3) Create `ShareLink` record (owner, docId, expiry, password_hash?, downloadAllowed)
* **Viewer:** Minimal responsive web page shows PDF (and thumbnails). If password set → gate before fetch.
* **Privacy-forward option (ADR-worthy):** **Zero-knowledge sharing**
    * Encrypt file client-side (AES-GCM). Put key in URL fragment (`#k=...`) so the server never sees it.
    * Pros: strong privacy. Cons: slightly more complex viewer.
* **AC:**
    * Create/revoke/expire works; opening expired or revoked link shows friendly error.
    * Owner can toggle download and watermark before sharing.
    * Opening a link does **not** require account (public viewer), unless “Require login” is enabled.

### 9.3 Phase C — Optional Sync (Later)

* **Doc metadata & thumbnails** sync across devices (same account).
* Conflict-safe: last-writer-wins for metadata; originals kept locally.
* Fully **opt-in** with clear data usage note.

### 9.4 Minimal Cloud Schema (if choosing Supabase)

* **tables**
    * `users(id, created_at, ... )` — managed by Auth
    * `documents(id, owner_id, title, page_count, updated_at)` — mirror of local metadata (optional, if sync)
    * `share_links(id, owner_id, doc_id, created_at, expires_at, password_hash, download_allowed boolean, revoked boolean)`
* **storage**
    * bucket `shares`: `/u/{owner_id}/{share_id}/{docId}.pdf` and `/pages/{index}.png`
* **RLS (sketch)**
    * `documents`: `owner_id = auth.uid()`
    * `share_links`: `owner_id = auth.uid()` for write; public read only via signed route (or edge function) that checks `revoked/expiry/password`.

### 9.5 API Surface (provider-agnostic sketch)

* `POST /shares`  → create share (docId, expiry, downloadAllowed, watermark, password?)
* `POST /shares/{id}/revoke` → revoke
* `GET  /v/{id}`  → viewer bootstrap (returns signed URLs or proxies fetch)
* **If zero-knowledge:** viewer gets ciphertext + key from URL fragment; decrypts client-side.

### 9.6 Client UX changes

* **Document screen:** “Share” button → dialog: {Expiry, Password?, Download toggle, Watermark toggle}
* **Share center:** list of active shares with counters (views, downloads), revoke button.
* **Settings:** “Sign in / Sign out”, “Cloud features” section, privacy notice.

### 9.7 Security & Compliance notes

* TLS only; block cleartext via Network Security Config.
* Do not embed API keys in code; pull from encrypted prefs after login.
* If password-protected share: store only hash (Argon2/BCrypt).
* Watermark server-side or client-side before upload; never modify originals.

### 9.8 Non-Goals (v1 of share)

* No collaborative editing.
* No public search/browse of shares.
* No long-term archival (links are time-boxed; user exports locally for permanence).

### 9.9 Milestone Checklist

* [ ] A: Auth ADR chosen and implemented; sign-in/out works offline-safe.
* [ ] B1: Create share flow; uploads; viewer renders.
* [ ] B2: Expiry/revoke/password; watermark; download toggle.
* [ ] B3: Analytics counters (optional).
* [ ] C: Sync ADR drafted (defer build if not needed now).

* **Cloud Sync**: Integrating cloud sync for documents, enabling cross-device access.
* **OCR Integration**: Incorporating OCR functionality for scanned documents.
* **Performance Optimizations**: Improving processing speeds for large documents.

## 10. Project Identification

**Project Name**: DocScan

**Repository URL**: [DocScan](https://github.com/NeiryAeris/DocScan)

**Primary Contact/Team**: [Insert Lead Developer/Team Name]

**Date of Last Update**: 2025-10-22

## 11. Glossary / Acronyms

* **OCR**: Optical Character Recognition
* **RBAC**: Role-Based Access Control
* **TLS**: Transport Layer Security
* **AES**: Advanced Encryption Standard

### **Cách sử dụng tài liệu này**:

* **ARCHITECTURE.md** sẽ được cập nhật khi có thay đổi lớn về kiến trúc hệ thống hoặc khi có quyết định mới về kiến trúc.
* Các **ADR** (Architecture Decision Records) sẽ bổ sung vào tài liệu này hoặc được lưu trữ riêng trong thư mục `docs/adr/`, để người đọc có thể theo dõi các quyết định quan trọng và lý do tại sao chúng được chọn.
