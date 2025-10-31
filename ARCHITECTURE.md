# Architecture Overview

## 1. Project Structure

This section provides a high-level overview of the project's directory and file structure, categorised by architectural layer or major functional area. It is essential for quickly navigating the codebase, locating relevant files, and understanding the overall organization and separation of concerns.

[Project Root]/
├── app/                      # Main Android application (UI/Compose, ViewModel, UI logic)
│   ├── src/
│   │   ├── main/
│   │   │   ├── androidManifest.xml
│   │   │   ├── java/com/example/app/   # UI components, ViewModels, Activities
│   │   │   ├── res/                 # Layouts, Drawables, etc.
│   │   └── build.gradle.kts       # Module-specific build configuration
├── domain/                   # Core logic, business models, and interfaces
│   ├── src/
│   │   ├── main/
│   │   │   ├── kotlin/com/example/domain/
│   │   │   │   ├── types/           # Entities, value classes
│   │   │   │   ├── interfaces/      # Interfaces for business logic
│   │   │   └── build.gradle.kts
│   └── build.gradle.kts       # Module-specific build configuration
├── pipeline-core/             # Image processing orchestration logic (core backend)
│   ├── src/
│   │   ├── main/
│   │   │   ├── kotlin/com/example/pipeline/
│   │   │   └── build.gradle.kts
├── imaging-opencv-android/    # Adapter for OpenCV image processing on Android
│   ├── src/
│   │   ├── main/
│   │   │   ├── kotlin/com/example/imaging/
│   │   │   └── build.gradle.kts
├── ocr-core/                         # Contracts OCR (pure Kotlin)
│   ├── src/
│   │   ├── main/
│   │   │   ├── kotlin/com/example/ocr/core/
│   │   │   │   ├── api/                     # OcrEngine, OcrResult, OcrPolicy, TextLayer models
│   │   │   └── build.gradle.kts
│   │   └── build.gradle.kts                 # Cấu hình module ocr-core
│
├── ocr-mlkit-android/                # OCR on-device (engine A)
│   ├── src/
│   │   ├── main/
│   │   │   ├── kotlin/com/example/ocr/mlkit/
│   │   │   └── build.gradle.kts
│   │   └── build.gradle.kts                 # Cấu hình module ocr-mlkit-android
│
├── ocr-tesseract-android/            # (Tuỳ chọn) OCR on-device (engine B – Tesseract)
│   ├── src/
│   │   ├── main/
│   │   │   ├── kotlin/com/example/ocr/tesseract/
│   │   │   └── build.gradle.kts
│   │   └── build.gradle.kts                 # Cấu hình module ocr-tesseract-android
│
├── ocr-remote/                       # (Tuỳ chọn) Client gọi OCR cloud (HTTP)
│   ├── src/
│   │   ├── main/
│   │   │   ├── kotlin/com/example/ocr/remote/
│   │   │   └── build.gradle.kts
│   │   └── build.gradle.kts                 # Cấu hình module ocr-remote
│
├── exporter-pdf-android/      # PDF export functionality for processed images
│   ├── src/
│   │   ├── main/
│   │   │   ├── kotlin/com/example/exporter/pdf/
│   │   │   └── build.gradle.kts
├── data-store-android/        # Local data storage with Room and file system management
│   ├── src/
│   │   ├── main/
│   │   │   ├── kotlin/com/example/datastore/
│   │   │   └── build.gradle.kts
├── docs/                      # Project documentation (e.g., API docs, setup guides)
├── scripts/                   # Automation scripts (e.g., deployment, data seeding)
├── .github/                   # GitHub Actions or other CI/CD configurations
├── .gitignore                 # Specifies intentionally untracked files to ignore
├── README.md                  # Project overview and quick start guide
└── ARCHITECTURE.md            # This document

## 2. High-Level System Diagram

The following diagram illustrates the key components of the system and how they interact:

[User] <--> [Frontend Application] <--> [Backend Service 1] <--> [Database 1]
                                    |
                                    +--> [Backend Service 2] <--> [External API]

This system consists of:

* **Frontend**: User-facing components that communicate with the backend services.
* **Backend Services**: APIs that handle business logic, data processing, and communication with external APIs and databases.
* **Database**: Stores application data, including processed images and metadata.
* **External Integrations**: Integration with third-party services as needed.

## 3. Core Components

### 3.1. Frontend

**Name**: Android Application (UI/Compose)

**Description**: The main user interface for interacting with the system, allowing users to scan documents, apply image processing, and export the results as PDF.

**Technologies**: Kotlin, Jetpack Compose, CameraX, WorkManager, Room

**Deployment**: Google Play Store (Android)

### 3.2. Backend Services

#### 3.2.1. Pipeline-Core

**Name**: Image Processing Core

**Description**: Orchestrates the sequence of image processing tasks such as deskewing, denoising, contrast enhancement (CLAHE), etc., without relying on Android SDK.

**Technologies**: Kotlin, OpenCV (for image processing logic)

**Deployment**: On-device (Android)

#### 3.2.2. Imaging OpenCV Android Adapter

**Name**: OpenCV Adapter for Android

**Description**: Adapts OpenCV functionality for Android, providing image manipulation methods like warp, deskew, CLAHE, etc.

**Technologies**: OpenCV, Android SDK

**Deployment**: On-device (Android)

#### 3.2.3. PDF Exporter

**Name**: PDF Exporter

**Description**: Converts processed images into a PDF file for download or sharing.

**Technologies**: Kotlin, Android PDF Document API

**Deployment**: On-device (Android)

### 3.3. Data Stores

#### 3.3.1. Local Storage (Room + File Storage)

**Name**: Data Store

**Type**: Room Database, File System

**Purpose**: Stores user data, including metadata about scanned documents and processed pages.

**Key Schemas/Collections**: `documents`, `pages`, `file_metadata`

## 4. Data Stores

### 4.1. Local Database

**Name**: Room Database

**Type**: Room (SQLite)

**Purpose**: Stores metadata for documents, pages, and scan processing status.

**Key Schemas**:

* `documents`: Stores document-level information.
* `pages`: Stores information for individual pages in a document.

### 4.2. File Storage

**Name**: File Storage

**Type**: Android File System

**Purpose**: Stores processed image files locally before exporting them to PDF.

## 5. External Integrations / APIs

### 5.1. Google Cloud Storage (for backup)

**Purpose**: Cloud-based storage for backing up processed documents and images.

**Integration Method**: Google Cloud Storage SDK

## 6. Deployment & Infrastructure

**Cloud Provider**: Google Cloud Platform (GCP)

**Key Services Used**: Firebase Authentication, Firebase Firestore, Firebase Storage, Google Cloud Functions (optional for future features)

**CI/CD Pipeline**: GitHub Actions

**Monitoring & Logging**: Firebase Crashlytics, Logcat

## 7. Security Considerations

### 7.1. Authentication

**Method**: Firebase Authentication (supports Google/Email sign-ins)

### 7.2. Authorization

**Method**: Role-Based Access Control (RBAC), Scoped data access per user (based on `uid`)

### 7.3. Data Encryption

* **In Transit**: TLS for data transfer
* **At Rest**: AES-256 for local data storage

## 8. Development & Testing Environment

### 8.1. Local Setup Instructions

Refer to `CONTRIBUTING.md` for setup instructions.

### 8.2. Testing Frameworks

* Unit testing: JUnit, MockK
* UI testing: Espresso, UI Automator
* End-to-end testing: Firebase Test Lab

### 8.3. Code Quality Tools

* Linting: ktlint
* Static analysis: Detekt
* Dependency checks: Gradle

## 9. Future Considerations / Roadmap

* **Cloud Sync**: Integrating cloud sync for documents, enabling cross-device access.
* **OCR Integration**: Incorporating OCR functionality for scanned documents.
* **Performance Optimizations**: Improving processing speeds for large documents.

## 10. Project Identification

**Project Name**: DocScan

**Repository URL**: [Insert Repository URL]

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
