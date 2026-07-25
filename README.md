# InterTrack Android Mobile Application

## Overview

InterTrack is an Android application for coordinating university internship workflows. It provides role-specific experiences for students, instructors, company supervisors, and administrators, with Firebase Authentication and Cloud Firestore supporting identity, account status, applications, messaging, reports, notifications, and verification workflows.

This repository is a sanitized public copy of the Android project. Machine-specific files, build output, signing material, databases, environment files, Git history, Firebase client configuration, and Firebase deployment configuration are intentionally excluded.

## Features

- Email and password registration, sign-in, sign-out, password reset, and password change
- Role-based navigation for students, instructors, company supervisors, and administrators
- Student and company verification workflows
- Internship offer publishing, browsing, editing, and application management
- Internship assignment and progress tracking
- Weekly report creation, submission, review, and status tracking
- Student, instructor, and company profile management
- In-app conversations and messages
- Notifications and account-status handling
- Administrative account, request, and university-change review

## Technologies

- Kotlin
- Android SDK (minimum SDK 24, target/compile SDK 36)
- JDK 17 or newer for Gradle (application bytecode targets Java 11)
- AndroidX and AppCompat
- Material Components
- XML layouts with View Binding
- Gradle Kotlin DSL and Gradle Wrapper
- Firebase Authentication
- Firebase Cloud Firestore
- Firebase Analytics
- JUnit, AndroidX Test, and Espresso

## Architecture

The application uses a conventional activity-and-fragment Android structure:

- **Activities** provide authentication, onboarding, account-status, verification, and role-specific dashboard containers.
- **Fragments** implement individual dashboard screens and user workflows.
- **Repositories** centralize authentication and Firestore operations.
- **Models** represent users, internship offers and applications, messages, reports, notifications, verification records, and related domain data.
- **Adapters** bind collection data to list-based UI components.
- **View Binding** provides type-safe access to XML views.

Firebase is accessed through repository classes rather than storing credentials in source code. Each developer must connect the application to their own Firebase project.

## Project Structure

```text
InterTrack-MobileAndroidApp-Public/
├── app/
│   ├── src/
│   │   ├── main/
│   │   │   ├── java/com/example/intertrack/
│   │   │   │   ├── activities/
│   │   │   │   ├── adapters/
│   │   │   │   ├── data/
│   │   │   │   ├── fragments/
│   │   │   │   ├── models/
│   │   │   │   ├── ui/
│   │   │   │   └── utils/
│   │   │   ├── res/
│   │   │   └── AndroidManifest.xml
│   │   ├── test/
│   │   └── androidTest/
│   ├── build.gradle.kts
│   └── proguard-rules.pro
├── gradle/
│   ├── libs.versions.toml
│   └── wrapper/
├── build.gradle.kts
├── settings.gradle.kts
└── gradle.properties
```

## Screens Implemented

### Authentication and onboarding

- Main/landing screen
- Login
- Registration
- Forgot password
- Role selection
- Account status
- Student verification
- Company verification

### Student

- Student dashboard and home
- Profile and account settings
- Explore internships and offer details
- Apply for internships
- Current and completed internships
- Internship progress hub
- Weekly reports and report details
- Instructor directory and requests
- Messages, chat, and notifications

### Instructor

- Instructor dashboard and home
- Profile editing
- Student requests and reviews
- Internship reports and report review
- Messages and chat

### Company supervisor

- Company dashboard and home
- Company and supervisor profiles
- Publish, edit, and manage internship offers
- Applications and applicant details
- Student and internship review workflows
- Messages and chat

### Administrator

- Admin dashboard and home
- User account management
- Pending account and verification requests
- Request details
- University change requests
- Admin profile

## Local Setup

1. Install Android Studio with Android SDK 36 and configure Gradle to use JDK 17 or newer. The application source compatibility remains Java 11.
2. Clone or download this repository and open its root folder in Android Studio.
3. Create a Firebase project and register an Android application with package name `com.example.intertrack`.
4. Download your Firebase `google-services.json` file and place it in `app/google-services.json`.
5. Enable Email/Password authentication and create a Cloud Firestore database in your Firebase project.
6. Define, review, and test Firestore security rules in your private Firebase deployment project before deployment.
7. Allow Gradle sync to complete, then run the `app` configuration on an emulator or Android device.

Do not commit `google-services.json`, Firebase CLI/deployment configuration, security rules, signing keys, `local.properties`, environment files, or exported databases. They are covered by `.gitignore`.

## Future Improvements

- Add dependency injection and formal ViewModel/state layers
- Expand automated unit, integration, and UI test coverage
- Add Firebase Cloud Messaging push delivery
- Add Firebase Storage for profile images and document attachments
- Improve offline synchronization and conflict handling
- Add accessibility, localization, and tablet layouts
- Introduce CI checks for builds, tests, lint, and secret scanning
- Configure release signing through secure CI secrets
- Add production monitoring, analytics consent, and crash reporting
