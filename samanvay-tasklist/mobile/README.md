# Samanvay Tasklist — mobile app

A native Android (and iOS) app for working on tasks, built with React Native and Expo SDK 52. It talks
to the same Samanvay Tasklist backend as the web app (`../`), and through it to the engine. Form
administration stays in the web app.

## Features

- **Sign-in with engine users and groups**, using the same session login as the web app. The app stores the server address and last username on the device (in `expo-secure-store`), never the password. The session cookie lives in the platform cookie store, so you stay signed in across restarts until the session expires.
- **Inbox:** Mine / Available / All, search, pull to refresh and infinite scrolling.
- **Task screen:** claim, release, and complete with a dynamic form.
- **Native form renderer** (`src/forms`) for the same JSON Schemas the web app renders with RJSF. It supports:
  - text, multiline, number and whole-number fields, switches
  - choices (`oneOf`/`const` or `enum`), shown as radio buttons
  - date and date-time pickers
  - read-only summaries, `ui:order`, `ui:help`
  - required fields, length and range limits, and `if`/`then`/`else` + `allOf` conditional rules

  The server re-validates every submission against the full schema. Its field errors appear next to the fields, so web and mobile enforce identical rules.
- **Branding:** light and dark themes in Samanvay colors (React Native Paper, Material 3).

## How it signs in

Native apps can't read the `XSRF-TOKEN` cookie the browser app uses. Instead the app:

1. fetches the CSRF token from `GET /api/auth/csrf`;
2. sends it in the `X-XSRF-TOKEN` header on writes;
3. leaves the session cookies to the platform.

`NativeClientSignInTest` in the backend covers this flow over real HTTP.

## Running on the Android emulator

Prerequisites:
- Node 20+
- The Android SDK with an emulator (Android Studio → Device Manager)
- The engine on `:8080` and the tasklist backend on `:8081` (see `../README.md`)

```bash
npm install
npx expo start --android --port 8082   # 8081 is taken by the tasklist backend
```

This installs Expo Go on the emulator and opens the app. The default server address is
`http://10.0.2.2:8081`, which is how the emulator reaches the host machine. Sign in with `demo` / `demo`.

**On a physical phone:** install Expo Go and scan the QR code that `npx expo start` shows. Then set the server address to your PC's LAN IP, e.g. `http://192.168.1.14:8081`. The phone and PC must be on the same network.

## Building an installable app

```bash
npx expo run:android            # local debug build (needs Android SDK + JDK 17)
npx eas build -p android        # signed APK/AAB via Expo Application Services (needs an Expo account)
```

**Production notes:**
- Use an HTTPS server address. Android release builds block plain HTTP unless you opt in.
- The backend's 30-minute session timeout applies on mobile too.
- iOS builds need a Mac with Xcode, or EAS Build.

## Tests

```bash
npm test          # Jest (jest-expo): form logic, renderer, formatting
npm run typecheck
```