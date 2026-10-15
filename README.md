# HBConnect

A Material Design 3 Android client for a self-hosted [Homebox](https://homebox.software)
inventory instance. API calls target the Homebox **v0.26+ "entities" API** (verified against the
v0.26.2 OpenAPI schema), where items and locations are unified as *entities* and *tags* replace
the older labels.

> Built on the same architecture as its sibling app LLConnect.

## Getting started

1. Build & install the app (Android Studio, or `./gradlew installDebug`).
2. Open **Server** from the navigation drawer and enter:
   - **Scheme** (HTTP/HTTPS), **Host / IP**, and optional **Port** (Homebox's default is `7745`).
   - **Username / password** — signs in via `/api/v1/users/login` and stores the returned bearer
     token on-device (encrypted); your password is never saved. Or switch to **API key** and paste
     a key created in Homebox under your profile.
3. Tap **Log in & connect** / **Save & test connection** to persist the details and verify them
   against `/api/v1/status` and `/api/v1/users/self`; the signed-in identity and server version
   appear below.

App preferences (theme, item-list order, offline-cache size) live under **Settings** and save the
moment you change them. The theme offers System / Light / Dark / Midnight, where Midnight is a dark
theme with pure-black surfaces for OLED screens.

Self-hosted instances are often reached over plain HTTP on a LAN address, so cleartext traffic is
enabled in the manifest.

## Architecture

- **UI:** Jetpack Compose + Material 3 (dynamic color on Android 12+), Navigation Compose, a
  navigation drawer, and a `ViewModel` + `StateFlow` per screen.
- **Networking:** Retrofit + OkHttp + Moshi. The base URL is built dynamically from the saved
  connection settings; an `AuthInterceptor` injects the `Authorization: Bearer …` header on every
  request (login returns the token already prefixed; API keys are normalized to carry one `Bearer`).
- **Storage:** DataStore holds the connection config. The bearer token / API key is encrypted with
  an AES-256/GCM key in the Android Keystore (`KeystoreCrypto`) — only ciphertext is written to disk.
- **DI:** a small manual `Graph` container initialized from `HBConnectApp`.

### Offline caching

GET responses are stored in an on-disk OkHttp cache so the app stays usable when the server can't
be reached. When online it always re-fetches fresh data; when the server is unreachable it serves
the cached copy instead.

- **Reachability** is tracked as `CHECKING → REACHABLE / UNREACHABLE`. A `ConnectivityManager`
  callback (`NetworkMonitor`) reacts to network changes instantly, and a lightweight background
  probe (`/api/v1/status`) resolves the state without ever blocking the UI. When a probe
  reconnects, screens auto-refresh to live data.
- **App-wide banner:** a neutral "Connecting…" bar while checking, and a red "Server unreachable —
  showing cached data" bar once it's confirmed down.
- **Settings → Offline cache:** shows current usage, a selectable maximum size (25 MB–500 MB), and
  a **Clear cache** button.

## Feature areas

| Area | Read | Add | Edit | Delete |
|------|:----:|:---:|:----:|:------:|
| Dashboard (group statistics) | ✅ | — | — | — |
| Items | ✅ | ✅ | ✅ | ✅ |
| Locations (+ browse contents) | ✅ | ✅ | ✅ | ✅ |
| Tags | ✅ | ✅ | ✅ | ✅ |
| Item attachments (view/open/upload) | ✅ | ✅ | — | ✅ |
| Item maintenance log | ✅ | ✅ | ✅ | ✅ |
| Server info / signed-in user / version | ✅ | — | — | — |

- **Dashboard** shows group statistics (items, locations, tags, with-warranty count, total value),
  from `/api/v1/groups/statistics`; the Items/Locations/Tags tiles jump to their lists.
- **Items** is a searchable list (server-side `q` query, debounced). Opening an item shows its
  photo, quantity, location, tags, identifiers, purchase/warranty/sold info, notes, and attachment
  names. The add/edit form covers name, description, quantity, location, tags, identifiers,
  purchase, and warranty. Because Homebox's update does a full field replace, fields the form
  doesn't expose (sold info, custom fields, asset id) are carried through from the loaded item so
  editing never wipes them.
- **Locations** lists every location with add / edit / delete; opening one lists the entities it
  contains, with an **Add item here** shortcut that pre-selects that location on the item form.
- **Tags** lists tags with inline add / edit / delete.
- **Attachments** (per item) — view a thumbnail/icon list, upload a photo or document via the
  system file picker, open an attachment in an external viewer (downloaded through the
  authenticated client and shared via a `FileProvider`), and delete.
- **Maintenance** (per item) — a log of scheduled/completed upkeep with name, description, cost,
  and dates; add / edit / delete.
- **Session** — login tokens are refreshed on launch when within a week of expiry
  (`/api/v1/users/refresh`); the Server screen has a **Log out** button that clears the stored
  session.

Entities are distinguished by their entity type's `isLocation` flag; the item list filters to
`isLocation=false` and the locations list to `isLocation=true`.

### Images & attachments

Item photos load through an authenticated Coil OkHttp client (carrying the bearer header) from
`/api/v1/entities/{id}/attachments/{attachmentId}`, cached in memory and on disk.

## Suggested next steps

- Tag-filtered item lists (the `tags` query param is already supported by the API client).
- Duplicate an item (`/api/v1/entities/{id}/duplicate`) and a breadcrumb path (`/entities/{id}/path`).
- Currency-aware price formatting (`/api/v1/currencies` + group currency).
- Manage entity types, and set an attachment as the item's primary photo.
- Import / export, notifiers, QR / label maker, and barcode product lookup.

## AI Transparency

This app was built with Claude Code, modeled on the LLConnect project's architecture.
