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

### Connection Modes

The SOP listener supports three connection modes:

| Mode | Behavior | Battery | Realtime |
|------|----------|---------|----------|
| **Stream** | Persistent SSE connection | High | Instant |
| **Polling** | Periodic API polling | Low | Delayed |
| **Dynamic** | Smart switching (default) | Medium | Near-instant |

**Dynamic mode** uses a state machine to balance battery usage and responsiveness:

- **Idle**: Polls the notification API at a configurable interval (1m / 5m / 10m)
- **Active**: Establishes SSE streaming when a new notification is detected
- Returns to Idle if the stream has no activity for the configured timeout (5m / 10m / 15m / 30m)

### SOP Notification Logger

The app logs received SOP notifications with timestamps. The log is viewable in Settings and stores the last 50 entries.

## Notification Grouping

CloudySky groups local notifications by metadata in this order:

- `room_id`
- `user_id`
- `topic`
- `type`

If a notification has media metadata, it can also show a banner image and a circular avatar:

- `pfp` is used for the sender avatar
- `image` is used as a single banner image
- `images` is treated as a multi-image set and rendered as a collage when possible

## Battery Optimizations

The app can request exemption from battery optimizations. This helps keep the foreground listener stable on devices that aggressively restrict background work.

A warning banner is shown on the main dashboard when battery optimizations are enabled.

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
