# HBConnect

A Material Design 3 Android client for a self-hosted [Homebox](https://homebox.software)
inventory instance. API calls target the Homebox **v0.26+ "entities" API** (verified against the
v0.26.2 OpenAPI schema), where items and locations are unified as *entities* and *tags* replace
the older labels.

> Built on the same architecture as its sibling app LLConnect.

## Getting started

1. Build and install the app (Android Studio, or `./gradlew installDebug`).
2. Open **Server** from the **More** tab and enter:
   - **Scheme** (HTTP/HTTPS), **Host / IP**, and optional **Port** (Homebox's default is `7745`).
   - **Username / password** - signs in via `/api/v1/users/login` and stores the returned bearer
     token on-device (encrypted); your password is never saved. Or switch to **API key** and paste
     a key created in Homebox under your profile.
3. Tap **Log in & connect** / **Save & test connection** to persist the details and verify them
   against `/api/v1/status` and `/api/v1/users/self`; the signed-in identity and server version
   appear below.

You can save **multiple servers** and switch between them from the selector at the top of the
Server screen; each connection (and its token) is stored separately.

App preferences live under **Settings** and save the moment you change them: the light/dark
**theme** (System / Light / Dark / Midnight, where Midnight uses pure-black surfaces for OLED), an
**accent colour** (Automatic / Material You, the Homebox palette, colour presets, or a custom
colour), the item-list order, and the offline-cache size.

Self-hosted instances are often reached over plain HTTP on a LAN address, so cleartext traffic is
enabled in the manifest.

## Architecture

- **UI:** Jetpack Compose + Material 3, Navigation Compose, a bottom navigation bar
  (Home / Items / Add / Locations / More), and a `ViewModel` + `StateFlow` per screen. The accent
  colour re-tints the Material 3 accent roles from a seed colour; Automatic uses Material You on
  Android 12+.
- **Networking:** Retrofit + OkHttp + Moshi. The base URL is built dynamically from the active
  connection; an `AuthInterceptor` injects the `Authorization: Bearer …` header and the active
  collection's `X-Tenant` header per request (a request may override the tenant, e.g. to rename a
  non-active collection).
- **Live updates:** an OkHttp WebSocket to `/api/v1/ws/events` refreshes open screens as data
  changes on the server. It connects only while the app is foregrounded (battery-friendly) and can
  be turned off in Settings.
- **Background work:** a WorkManager job checks scheduled maintenance and posts due reminders even
  when the app is closed.
- **Storage:** DataStore holds app preferences and the saved servers. Each server's bearer token /
  API key is encrypted with an AES-256/GCM key in the Android Keystore (`KeystoreCrypto`) - only
  ciphertext is written to disk. A local **Room** database keeps an offline copy of the inventory
  and the pending upload queue.
- **DI:** a small manual `Graph` container initialized from `HBConnectApp`.

### Offline support

The app keeps working without the server:

- **Local database (Room).** A background sync caches the whole collection on device - every item
  and location, their full detail, each location's contents, and tags - on launch and whenever the
  server reconnects. It is incremental, so only changed entities are re-fetched. When the server is
  unreachable the lists, item/location detail, a location's contents, and search all read from this
  copy instead of failing. Item thumbnails are warmed into the image cache so they show offline too.
- **Upload queue.** Changes made while offline (add / edit / delete for items, locations, tags,
  entity types, templates, maintenance, and photos) are saved to a durable outbox and uploaded
  automatically when the server returns, in order. **More → Queue** lists what's waiting - including
  the photos queued with a new item - with a manual retry and a remove action. The queue survives a
  force-close and only replays into the collection it was created in. Settings can post a
  notification once a queued batch finishes uploading.
- **Sync status.** The dashboard's top-right shows at a glance whether the local copy is synced and
  online, up to date but offline, or has changes still to upload; tap it to open the queue.

Underneath, GET responses are also kept in an on-disk OkHttp cache. **Reachability** is tracked as
`CHECKING → REACHABLE / UNREACHABLE`: a `ConnectivityManager` callback (`NetworkMonitor`) reacts to
network changes instantly and a lightweight `/api/v1/status` probe resolves the state without
blocking the UI. On reconnect the "Connecting…" banner clears and open screens refresh to live data
at once. **Settings → Offline cache** shows usage, a selectable maximum size (25 MB-500 MB), and a
**Clear cache** button.

## Feature areas

| Area | Read | Add | Edit | Delete |
|------|:----:|:---:|:----:|:------:|
| Dashboard (statistics, search, scan) | ✅ | — | — | — |
| Items (search, filters, bulk actions) | ✅ | ✅ | ✅ | ✅ |
| Locations (+ browse contents) | ✅ | ✅ | ✅ | ✅ |
| Tags | ✅ | ✅ | ✅ | ✅ |
| Entity types | ✅ | ✅ | ✅ | ✅ |
| Templates | ✅ | ✅ | ✅ | ✅ |
| Item attachments (view / open / upload) | ✅ | ✅ | — | ✅ |
| Item maintenance log | ✅ | ✅ | ✅ | ✅ |
| Collections (create / switch / join / rename) | ✅ | ✅ | ✅ | — |
| Members & invitations | ✅ | ✅ | — | ✅ |
| Account (profile, password, API keys) | ✅ | ✅ | ✅ | ✅ |
| Currency | ✅ | — | ✅ | — |

### Catalogue

- **Dashboard** shows group statistics (items, locations, tags, with-warranty count, total value)
  with a server-wide search, saved search filters (archived, photo, tags, custom fields), and a
  barcode / asset scanner. A sync-status indicator at the top right shows whether the offline copy
  is synced, offline but current, or has queued changes to upload, and taps through to the queue.
- **Items** is a searchable, sortable list with bulk select (move, tag, set quantity, delete).
  Opening an item shows its photo gallery, quantity, location, tags, identifiers,
  purchase/warranty/sold info, notes, and attachments, plus **duplicate**, a location breadcrumb,
  maintenance, attachments, and a QR / label action. Edits carry through fields the form doesn't
  expose so nothing is wiped.
- **Locations** lists every location with add / edit / delete; opening one lists the entities it
  contains, with an **Add item here** shortcut.
- **Tags**, **Entity types**, and **Templates** each have inline add / edit / delete; templates can
  create a new item from their defaults.
- **Attachments** (per item) - a thumbnail/icon list; upload a photo or document via the system
  picker, open in an external viewer, and delete.
- **Maintenance** (per item) and a **group-wide maintenance** view - scheduled/completed upkeep
  with name, description, cost, and dates. Optional **reminders** notify you when upkeep is due.
- **Value breakdown** (More → Tools) - total value plus breakdowns by location, tag, manufacturer
  and type, the most expensive items, insured vs uninsured, and a cumulative value-over-time chart.

### Collections, members & currency

- **Collections** - create a new collection, switch the active one (sent as `X-Tenant`), join
  another from an invite code or link, and rename collections. New items, locations and tags go
  into the active one.
- **Members** - list members, invite new ones (with a chosen use count and expiry), and remove
  members or revoke invitations.
- **Currency** - pick the group's currency from the supported list (common currencies surfaced on
  top); prices throughout the app format against it.

### Account & servers

- **Account** - edit your profile, change your password, manage personal API keys (create with an
  optional expiry, shown once, and delete), and log out of all login sessions.
- **Multiple servers** - save several connections and switch between them; switching re-points the
  client and clears the previous server's cached data.

### Tools

Under **More → Tools**:

- **Import & export** - move items in or out as a CSV spreadsheet.
- **Bill of materials** - download a CSV report of every item and its value.
- **Backup & restore** - whole-collection backups generated on the server; the list shows each
  backup's date, status and size, and you can download a finished one to this device or restore from
  a backup file. (Homebox's own backup service has a known server-side bug in v0.26.x where it works
  only once per container restart.)
- **Logs** - an in-app diagnostic log of the app's HTTP activity (Homebox does not expose its server
  logs over the API), with live search and level filters.

The **More** tab is alphabetical with Collections pinned at the top and Server / Settings / Account
at the bottom; the middle entries can be dragged into a custom order (with a reset to A-Z).

### Images & attachments

Item photos load through an authenticated Coil OkHttp client (carrying the bearer and tenant
headers) from `/api/v1/entities/{id}/attachments/{attachmentId}`, cached in memory and on disk.

## AI Transparency

This app was built with Claude Code, modeled on the LLConnect project's architecture.
