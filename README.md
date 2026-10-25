# MovieFlix – A Movie Streaming App

MovieFlix is a feature-rich Android OTT (Over-The-Top) streaming application built with **Kotlin** and **Jetpack Compose**. It provides a complete video-on-demand experience with subscription management and **eSewa** payment integration, so users in Nepal can subscribe without needing an international credit card. The app has a dual-interface design: one for regular **users** and one for **administrators**.

> Final-year project submitted to the Department of Computer Science and Engineering, **Nepal Engineering College**, in partial fulfillment of the requirements for the degree of B.E. in Computer.

---

## Table of Contents

- [Features](#-features)
- [Tech Stack](#-tech-stack)
- [Architecture](#-architecture)
- [Getting Started](#-getting-started)
- [Configuration](#-configuration)
- [Testing](#-testing)
- [Project Scope & Limitations](#-project-scope--limitations)
- [Future Work](#-future-work)
- [Team](#-team)
- [License & Legal Notes](#-license--legal-notes)

---

## Features

###  User Features
- **Authentication** – Email/password sign-up with email verification, Google sign-in, and password reset via Firebase.
- **Profile management** – Complete profile (name, phone, DOB) with custom profile image upload.
- **Movie browsing** – Real-time catalogue with search, filters, and categories (All, Top Rated, Newly Added, Recommended).
- **Dual video players** – ExoPlayer and WebView player with playback controls, seek, and speed control.
- **Subscriptions** – Tiered plans purchased through eSewa, with automatic activation and expiry tracking.
- **Access control** – Active subscribers watch full movies; non-subscribers can watch trailers only.
- **Favourites** – Add/remove favourites with instant sync.
- **Watch history** – Tracks watched movies.
- **Personalized recommendations** – Content-based and collaborative filtering ("Recommended For You", "Users Also Watched").

###  Admin Features
- **Real-time dashboard** – Total users, active subscriptions, movies, revenue, and a 7-day revenue chart.
- **Movie management** – Add movies by searching TMDb (auto-populates poster, description, rating, genres), edit, and delete.
- **User management** – View users, change roles, ban/unban (with forced logout).
- **Subscription management** – View active / expiring / expired subscribers and create, edit, or delete subscription plans.
- **Activity audit log** – Records admin actions and key user events (purchases, bans, new movies, etc.).
- **Role-based access control (RBAC)** – Admin panel is hidden from regular users.

---

##  Tech Stack

| Layer | Technology |
|---|---|
| Language | Kotlin |
| UI | Jetpack Compose, Material Design 3 |
| Architecture | MVVM |
| Backend (BaaS) | Firebase Authentication, Cloud Firestore, Firebase Storage, Firebase Analytics |
| Video playback | ExoPlayer, WebView player |
| Movie metadata | [The Movie Database (TMDb) API](https://www.themoviedb.org/documentation/api) |
| Payments | eSewa Android SDK |
| Build system | Gradle, Android Studio |

---

##  Architecture

MovieFlix follows a client–server model where the Android app is the client and Firebase is the backend.

```
┌───────────────┐        ┌──────────────────────────────┐
│  Android App  │◄──────►│  Firebase                    │
│  (Kotlin +    │        │  • Authentication            │
│   Compose,    │        │  • Firestore (real-time DB)  │
│   MVVM)       │        │  • Storage                   │
└──────┬────────┘        └──────────────────────────────┘
       │
       ├──────────► eSewa SDK        (subscription payments)
       ├──────────► TMDb API         (movie metadata for admins)
       └──────────► ExoPlayer / WebView (video playback)
```

The Firestore data model is relational in spirit, connecting **users**, **subscription plans**, and **active subscriptions**, alongside movies, favourites, watch history, and activity logs.

---

##  Getting Started

### Prerequisites
- [Android Studio](https://developer.android.com/studio) (latest stable)
- JDK 17 (or the version required by your Android Gradle Plugin)
- An Android device or emulator (physical device recommended for testing playback and payments)
- A Firebase project
- A TMDb API key
- eSewa developer / test credentials

### Installation

```bash
# 1. Clone the repository
git clone https://github.com/manish11111111/MovieFlix.git
cd MovieFlix

# 2. Open the project in Android Studio and let Gradle sync
```

---

##  Configuration

### 1. Firebase
1. Create a project in the [Firebase Console](https://console.firebase.google.com/).
2. Add an Android app using the project's package name.
3. Download `google-services.json` and place it in the `app/` directory.
4. Enable the following:
   - **Authentication** → Email/Password (and Google, if using Google sign-in)
   - **Cloud Firestore**
   - **Firebase Storage**
5. Configure **Firestore security rules** properly. Misconfigured rules are a leading cause of Firebase data leaks.
6. Create at least one user and set their `role` to `admin` in Firestore to access the admin panel.

### 2. TMDb API
Add your key to `local.properties` (do **not** commit this file):

```properties
TMDB_API_KEY=your_tmdb_api_key
```

### 3. eSewa
Add your eSewa test credentials (client ID / secret / merchant details) to `local.properties` or wherever the project reads its payment config:

```properties
ESEWA_CLIENT_ID=your_client_id
ESEWA_CLIENT_SECRET=your_client_secret
```

Use the eSewa **test environment** during development. For production, you must register as an eSewa merchant.

### 4. Run
Select a device/emulator and press **Run ▶** in Android Studio.

---

##  Testing

Manual test cases performed during development (all passed):

| # | Test Case | Result |
|---|---|---|
| 1 | User registration & login (email verification) |  Pass |
| 2 | Complete user profile |  Pass |
| 3 | Browse & search movies |  Pass |
| 4 | Video playback (ExoPlayer) |  Pass |
| 5 | Purchase subscription via eSewa |  Pass |
| 6 | Subscription access control (full movie vs. trailer) |  Pass |
| 7 | Add / remove favourites |  Pass |
| 8 | Admin add movie (TMDb API) |  Pass |
| 9 | Admin ban / unban user |  Pass |
| 10 | Role-based access control |  Pass |
| 11 | Reset password |  Pass |

---

##  Project Scope & Limitations

- Android only – no iOS, web, or TV versions.
- Video content is streamed from external sources (e.g., Firebase Storage) rather than self-hosted servers.
- Recommendation system uses content-based and collaborative filtering; it is not comparable to large-scale ML systems.
- Payments currently limited to **eSewa**.
- No offline downloads, live streaming, or multiple profiles per account.

---

##  Future Work

- **Offline downloads** using ExoPlayer's `DownloadManager` with automatic expiry for subscription content.
- **Multi-user profiles** with separate watch history and PIN-protected parental controls.
- **AI-powered recommendations** using TensorFlow Lite.
- **More payment options** – Khalti, IME Pay, and international gateways (Stripe/PayPal).
- **Subtitles & localization** – multi-language subtitles, audio tracks, and app translations (Nepali, English, Hindi).
- **Cross-platform expansion** – iOS, web, and Android TV.

---

##  Team

| Name | Roll No. |
|---|---|
| Manish Chapagain | 021-338 |
| Luthan Hang Chongbang | 021-336 |
| Manjil Bhandari | 021-340 |

**Supervisor:** Asst. Prof. Susant Bhujel
**Institution:** Department of Computer Science and Engineering, Nepal Engineering College

---

##  License & Legal Notes

- This project was built for **educational purposes**. Add a `LICENSE` file that matches how you want others to use the code.
- Demo content uses sample / public-domain videos. Any commercial deployment requires proper **licensing agreements** with content owners.
- The TMDb API is free for **non-commercial** use only; commercial use requires TMDb approval. *This product uses the TMDb API but is not endorsed or certified by TMDb.*
- Live payments require merchant registration with eSewa and compliance with Nepal Rastra Bank regulations.
- Payment details are handled by eSewa and are **not** stored in the app's database. Passwords are hashed by Firebase Authentication.
