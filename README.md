# CloudySky

CloudySky is an Android client for Solian notifications and account access.

## Features

- OIDC sign-in with AppAuth
- Secure token storage
- Notification feed from the Solian API
- Settings screen with account info and listener controls
- SOP listener for real-time notifications via SSE

## SOP Listener

CloudySky supports the SOP push flow described in `docs/SOP_PUSH_API.md`.

Behavior:

- registers a SOP subscription after sign-in
- keeps a foreground service alive to listen for SSE notifications
- shows local Android notifications for incoming events
- opens notification deep links using `actionUri`
- can resume after boot when the listener is enabled

## Battery Optimizations

The app can request exemption from battery optimizations. This helps keep the foreground listener stable on devices that aggressively restrict background work.

## Development

Open the project in Android Studio and run the `app` configuration.

Build from the command line:

```bash
./gradlew :app:assembleDebug
```

## Notes

- OAuth client configuration is read from `local.properties`.
- The app uses `https://api.solian.app` as the default API base.
- Notification list fetching currently remains bearer-auth based.
