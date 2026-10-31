# Product Requirement Document (PRD)
## Privacy-First Local "Memories" & Home Screen Widget App

Status: Revised for feasibility. Monetization deferred (see section 9).

---

## 1. Executive Summary & Vision

### 1.1 Product Vision
Deliver a Google Photos-grade "Memories" and smart nostalgia experience that runs entirely on the phone. Photos and everything learned about them (search index, faces, places) never leave the device. The app uses on-device AI models to curate past photos, answer natural language searches, and power dynamic Android home screen widgets.

### 1.2 Core Value Proposition
- **Local Only, Private by Design:** No cloud servers, no account, and no `android.permission.INTERNET` in the final app. This is enforced by a build check, not just a promise (see 5.4).
- **Smart Memory Curation:** "On This Day", junk and receipt filtering, and automatic event and trip grouping, all computed locally.
- **Widget-First Experience:** Dynamic, customizable home screen widgets built with Jetpack Glance.
- **Natural Language Search:** Find photos with queries like *"red car with people in front"* or *"sleeping cat on couch"* using on-device image embeddings.

---

## 2. Target Audience & Market Positioning

### 2.1 Target Personas
1. **The Privacy-Conscious User:** Wants smart photo organization but does not want their photos, faces, or locations processed on someone else's servers.
2. **The Nostalgia & Personalization Enthusiast:** Loves home screen widgets, photo carousels, and memory summaries.
3. **The Subscription-Weary User:** Wants smart features without paying monthly for cloud storage (Google One, iCloud).

### 2.2 Competitive Landscape
| Feature | Google Photos | OEM Galleries (Samsung, Pixel, Xiaomi) | Privacy Galleries (Aves, Fossify) | **Our App** |
| :--- | :--- | :--- | :--- | :--- |
| **Where processing happens** | Mostly cloud | Mixed device and cloud | Device | **Device only** |
| **Natural language search** | High | Medium (some on-device) | None | **High (on-device CLIP)** |
| **Memories / stories** | High | Medium | None | **High** |
| **Memory widgets** | Fixed | Basic | Basic or none | **Customizable** |
| **Network permission** | Required | Required | Varies | **None** |

Positioning: OEM galleries already do some on-device AI but are tied to one brand and still connect to cloud services. Privacy galleries are local but have no smart features. We combine both.

---

## 3. Product Architecture & AI Strategy

### 3.1 On-Device AI Pipeline

                   ┌────────────────────────────────────────┐
                   │          Raw Local Media               │
                   │   (MediaStore, EXIF, GPS via           │
                   │    ACCESS_MEDIA_LOCATION)              │
                   └───────────────────┬────────────────────┘
                                       │
                                       ▼
                   ┌────────────────────────────────────────┐
                   │   Background Pipeline (WorkManager)    │
                   │  Constraints: Charging + Device Idle   │
                   │  Thermal status check between batches  │
                   └───────────────────┬────────────────────┘
                                       │
         ┌─────────────────────────────┼─────────────────────────────┐
         │                             │                             │
         ▼                             ▼                             ▼

┌─────────────────────────┐   ┌─────────────────────────┐   ┌─────────────────────────┐
│ Tier 1: Junk Filter     │   │ Tier 2: Image Embeddings│   │ Tier 3: Faces           │
│ (heuristics + ML Kit    │   │ (MobileCLIP-S0 / LiteRT)│   │ (ML Kit detect +        │
│  bundled OCR)           │   │                         │   │  MobileFaceNet embed)   │
├─────────────────────────┤   ├─────────────────────────┤   ├─────────────────────────┤
│ - Screenshots (path)    │   │ - 512-d INT8 vector     │   │ - Detect + crop faces   │
│ - Blur (Laplacian)      │   │ - Text-to-image search  │   │ - Face embedding vector │
│ - Receipts / docs (OCR) │   │ - Category chips, pets  │   │ - Cluster into people   │
└────────────┬────────────┘   └────────────┬────────────┘   └────────────┬────────────┘
             │                             │                             │
             └─────────────────────────────┼─────────────────────────────┘
                                           │
                                           ▼
                     ┌────────────────────────────────────────┐
                     │        Local Room (SQLite) Database    │
                     │  Metadata, embeddings (BLOB), faces,   │
                     │  people, events, exclusion rules       │
                     └───────────────────┬────────────────────┘
                                         │
                                         ▼
                    ┌────────────────────────────────────────┐
                    │    Widget Engine & UI Presentation     │
                    │   (Jetpack Glance & Jetpack Compose)   │
                    └────────────────────────────────────────┘

All models are bundled with the app and run in the app's own process. Nothing is routed through Google Play services, so the app does not depend on Google services for AI features.

#### Tier 1: Junk Filter
- **Screenshots:** MediaStore `RELATIVE_PATH` contains `Screenshots`, or image dimensions match the device screen. No model needed.
- **Blurry photos:** Laplacian variance on a downsampled image. No model needed.
- **Receipts and documents:** ML Kit Text Recognition (bundled variant). High text density plus a CLIP zero-shot check (*"a photo of a receipt"*, *"a photo of a document"*).
- **Rule:** Junk is never deleted. It is only excluded from memories and widgets. Users can mark any photo "Not junk", which overrides the filter permanently.

#### Tier 2: Image Embeddings (Search Engine)
- **Model:** MobileCLIP-S0, INT8 quantized, run with LiteRT. Image encoder plus text encoder are roughly 55 MB combined. Confirm the weights' license allows commercial use before launch.
- **Output:** One 512-dimension vector per photo, stored as INT8 (512 bytes per photo; 50,000 photos is about 25 MB).
- **Search method:** Load all vectors into memory and compare against the query with a full scan (dot product on normalized vectors). At 50,000 photos this is a few milliseconds and needs no SQLite extension.
- **Reuse:** The same vectors power category chips (*Pets*, *Food*, *Documents*) and pet detection via zero-shot text prompts.
- **Limitation:** MobileCLIP understands English queries only. Non-English search is deferred.

#### Tier 3: Face Detection & Clustering
- **Detection:** ML Kit Face Detection (bundled variant) finds faces and landmarks. It does not recognize who someone is.
- **Recognition:** Each face crop is aligned and passed through MobileFaceNet (about 4 MB) to produce a face embedding.
- **Clustering:** New faces join the nearest existing person if close enough; otherwise they start a new cluster. A full re-cluster (DBSCAN) runs periodically while charging.
- **User control:** Users name, merge, and split people. Face data never leaves the device.
- **Pets:** Pets are found with CLIP prompts (*"a photo of a dog"*, *"a photo of a cat"*). Telling individual pets apart is out of scope for v1.

### 3.2 Model Delivery
- Models ship in a Play Asset Delivery **install-time** asset pack, downloaded by the Play Store together with the app. The app itself still needs no network permission.
- Each stored embedding records the model version. When a model is upgraded, photos are re-indexed in the background and the old index stays usable until re-indexing finishes.

---

## 4. Key Functional Requirements

### 4.1 Gallery Basics (free, foundational)
- Date-grouped photo grid and full-screen viewer with pinch-zoom.
- Share via the Android share sheet. Sharing is always user-initiated and handled by the app the user picks.
- Delete via the system trash (`MediaStore.createTrashRequest` on Android 11+; confirmed delete on Android 10).
- Favorites (MediaStore `IS_FAVORITE` on Android 11+; app-local favorites on Android 10).
- Edit by handing off to an installed editor (`ACTION_EDIT`). No built-in editor in v1.
- SD card photos are included automatically through MediaStore storage volumes.

### 4.2 Memory Engine
- **"On This Day":** Photos taken on the current month and day (plus or minus 3 days) in previous years. Works from MediaStore dates alone, so it works on day one, before AI indexing.
- **Events:** Photos are split into events wherever there is a time gap of more than 3 hours (tunable).
- **Trips ("Getaways"):** An event that is more than 100 km from the user's home area (the most frequent photo location) and spans at least one night.
- **Place names:** Bundled offline city dataset (GeoNames, CC BY 4.0, attribution required) gives names like *"Trip to Lisbon"*. Android's built-in Geocoder is not used because it needs the network.
- **Junk exclusion:** Junk photos (3.1 Tier 1) and hidden or vaulted photos never appear in memories or widgets.

### 4.3 Home Screen Widgets (Jetpack Glance)
- **Daily Memory Widget:** Shows "On This Day" or a highlight memory. Tap arrows to move between photos; the set rotates on each refresh.
- **Refresh options:** Every hour, every 6 hours, or daily. (Android widgets cannot reliably update on every unlock, and the system minimum is 30 minutes.)
- **Image limits:** Widget images are downsampled to the widget's size, and each carousel holds at most 5 photos, to stay within Android's widget memory limit.
- **Tap-to-Story:** Tapping the widget opens a lightweight full-screen story viewer for that memory.
- **Widget Studio (Phase 4):** Choose source (On This Day, a person, pets, favorites, a phone folder, or a user album from 4.7), refresh rate, and style (Material You dynamic colors, corner radius, border).

### 4.4 Search & Discovery
- **Natural Language Search:** English queries ranked by CLIP similarity. Results available for photos indexed so far, with a visible "X of Y photos indexed" indicator.
- **Category Chips:** *Pets*, *Food*, *Documents*, *Trips*, *Screenshots*.
- **People Hub:** Face clusters that users can name, merge, and split.

### 4.5 Privacy Controls
- **Hidden photos (Phase 1):** A simple "Hide" flag. The file stays where it is but is excluded from memories, widgets, and search.
- **Sensitive Vault (Phase 4):** Photos are encrypted (AES-GCM, key in Android Keystore) and moved into app-private storage. The originals are removed from the gallery with the system delete dialog.
  - Unlock requires Class 3 (strong) biometrics or the device PIN. Many face unlocks are Class 2 and are not accepted.
  - The vault key must survive fingerprint changes (not invalidated on new biometric enrollment), with the device PIN as fallback, so adding a fingerprint never locks the user out.
  - **Data loss warning:** Vault contents are destroyed if the app is uninstalled or its data is cleared. Onboarding must say this clearly, and an "Export vault back to gallery" action must always be available.
- **Exclusion rules:** Users can exclude folders, date ranges, or people from memories and widgets.

### 4.6 Memory Reminders (Notifications)
Local notifications that bring users back to their photos. All scheduling and content are computed on the phone; nothing is pushed from a server.

**Two kinds**
- **Memory nudge:** points to a real memory and shows a thumbnail from it. Sent only when at least one memory line below fits today (junk, hidden, vaulted, and excluded photos never count). Tapping opens that memory's story. Skipped if the user already opened a memory today.
- **Capture nudge:** encourages taking a photo today. Sent whether or not a memory exists. Tapping opens the phone's camera app.

**Memory lines** (title, then a short second line; N and the place are filled in)
| Family | Fits when | Title | Second line | Arrives in |
| :--- | :--- | :--- | :--- | :--- |
| Place | A trip anniversary is within plus or minus 3 days | "That time in {place}" | "N years ago this week." | Phase 3 |
| | | "Back in {place}" | "Relive the trip." | Phase 3 |
| Get-together | Today's memory is mostly group photos | "A get-together to remember" | "You and your people, N years ago." | Phase 2 |
| Nature | Today's memory is mostly outdoor or nature photos | "A moment with nature" | "Some fresh air from N years ago." | Phase 2 |
| Food | Today's memory is mostly food photos | "Remember this meal?" | "From this week, N years ago." | Phase 2 |
| Pets | Today's memory is mostly pet photos | "A furry throwback" | "Look who it was, N years ago." | Phase 2 |
| Years ago | An On This Day memory exists | "On this day, N years ago" | "See what you were up to." | Phase 1 |
| | | "N years ago today" | "A moment worth another look." | Phase 1 |
| A month ago | Photos exist from 30 days ago, plus or minus 1 day | "This time last month" | "Remember this?" | Phase 1 |

**Nudge lines**
| Title | Second line | Allowed time |
| :--- | :--- | :--- |
| "Make new memories!" | "Snap something today for next year's you." | Any |
| "What are you up to today?" | "Take a photo and see it again next year." | Before 3 PM |
| "What was your highlight today?" | "Capture it before the day ends." | After 5 PM |
| "Future you will thank you" | "One photo today is a memory next year." | Any |

**Rules**
- **Most specific wins:** when several families fit, use place, then theme (get-together, nature, food, pets; the highest scoring), then years ago, then a month ago. Within a family, the line is chosen at random.
- **No repeats:** the same line is not used again within 7 days. If every fitting line was used in that time, the least recently used one is picked.
- **Frequency:** At most two notifications a day, one of each kind.
- **Timing:** Each day gets two random times between 9 AM and 9 PM local time, at least 3 hours apart, in random order. Nudge lines are filtered by their allowed time at the moment they are sent. Inexact delivery (a few minutes late) is acceptable, so the app does not need the restricted exact alarm permission. A reminder delayed past 9:30 PM (for example by battery saving while the phone is idle) is skipped, never sent late at night.
- **Controls:** A Settings switch for each kind, both on by default. Each kind has its own Android notification channel so it can also be managed in system settings.
- **Permission:** `POST_NOTIFICATIONS` (Android 13+) is requested the first time a memory exists, or when the user turns on a switch. Never during onboarding.
- **Lock screen:** The public version never shows a photo. Place and get-together lines show "A memory is waiting" instead of their title, so a locked phone does not reveal places or people. Other lines show their title.
- **Translation:** All lines live in string resources, with plurals for N.

### 4.7 Albums
A new **Albums** tab in the bottom navigation, with three sections in this order: User, Default, Rotating. Removing or deleting an album never deletes a photo; photos always stay in the gallery. "On This Day" is not an album; it stays its own feature (4.2, 4.3, 4.6).

#### User albums
- Created from the Albums tab ("New album"), or from a photo or a selection ("Add to album", with "New album" at the top of the picker).
- Inside a user album: rename, choose a cover photo (default: the newest photo), remove photos from the album, and delete the album.
- A photo can be in any number of albums. Photos are shown newest first.
- No limit on the number of user albums. They change only when the user edits them.
- A pinned rotating album becomes a user album (see Rotating albums).
- Album membership is removed only when a photo is permanently deleted or moved to the vault. Photos that are in the system trash or no longer accessible (for example, deselected under partial access) are skipped while unavailable and return to their albums when they come back. Hidden photos stay in the album's list but are shown only in the Hidden album (4.5), as everywhere else.
- A user album shows every photo the user added, including junk and photos covered by exclusion rules, because the user put them there on purpose. As a widget source (4.3), exclusion rules still apply, since widgets appear without being asked for. If that leaves no photos, the widget says so and offers to choose another source.

#### Default albums
A fixed set, built from flags the app already computes. Each one appears only once it has at least one photo.
| Album | Contents | Available from |
| :--- | :--- | :--- |
| Favorites | Favorite photos (4.1) | Phase 1 |
| Screenshots | Screenshots that are not chat screenshots | Phase 1 (by path), Phase 2 (full) |
| Chat screenshots | Screenshots of message conversations, found with a CLIP zero-shot check (*"a screenshot of a text message conversation"*, *"a screenshot of a chat app"*) once the photo is indexed | Phase 2 |
| Receipts & documents | Photos flagged as receipt or document (3.1 Tier 1) | Phase 2 |
| Blurry | Photos flagged as blurry (3.1 Tier 1) | Phase 2 |
| Rediscover | Up to 50 ungrouped photos, refreshed every 2 days (see below) | Phase 3 |
- The four junk albums (Screenshots, Chat screenshots, Receipts & documents, Blurry) never include photos marked "Not junk", hidden, or vaulted. Each photo is in at most one junk album, following the junk precedence in 3.1.
- Junk albums are for finding and cleaning up: they support "Select all", then Trash (system trash dialog, 4.1).
- **Android 10:** there is no system trash or batch delete for photos the app did not create, so each photo needs its own confirmation. Bulk Trash is capped at 20 photos per action on Android 10, with a note that newer Android versions can trash more at once. No extra storage permission is requested.
- The user can remove any default album (long-press, "Remove album"). It comes back only through Settings, "Restore default albums".
- Default albums cannot be renamed. Only Rediscover has a photo limit.
- Exclusion rules (4.5) do not apply to Favorites or the junk albums, which are for finding photos. They do apply to Rediscover and rotating albums, which behave like memories.

#### Rotating albums
Suggested albums built from the smart groups, so the Albums tab keeps surfacing something new.
- **Candidates**, each with a stable key so it is never repeated:
  - A trip (4.2), titled with its trip name, for example *"Trip to Lisbon, 2024"*.
  - A named person in one calendar year, for example *"Ana in 2023"*. Unnamed face clusters are not candidates.
  - Pets in one calendar year, for example *"Pets in 2023"*.
- **Eligible photos:** the same rules as memories (4.2): no junk, hidden, vaulted, excluded (4.5), or video items.
- **Size:** a candidate is published only if it has at least 30 eligible photos. A published album holds at most 50.
- **Choosing the 50:** spread evenly across the candidate's time span, favorites first, then photos with faces, skipping near-duplicates (taken within 2 seconds of each other).
- **Frozen:** once published, the photo list never changes and is never regrouped. Photos later deleted, hidden, vaulted, or excluded are skipped when shown.
- **Rotation:** one new album every 2 days, at most 5 at a time. When a sixth is published, the one published longest ago is removed. If no candidate is eligible, the turn is skipped.
- **Picking the next candidate:** take turns between kinds (trip, person, pets), skipping kinds with no eligible candidate. Within a kind, pick at random among candidates whose photos are at least 1 year old, or among all eligible candidates if none are.
- **No repeats:** every published candidate key is remembered permanently, even after the album is removed, so the same trip or person-year never returns.
- **Pin:** moves the album to User albums with the same title and photos. From then on it is a normal user album, and its rotating slot frees up.
- **Dismiss:** the user can remove a rotating album early. It counts as published, so it does not return.

#### Rediscover
- Up to 50 random photos that are not part of a smart group: not in a trip, not showing a named person who has 30 or more eligible photos, not a pet photo, and not in a current rotating album. Ordinary (non-trip) events never count as grouped, so Rediscover is a broad random sample of the library.
- Same eligibility as rotating albums.
- A new set every 2 days. A photo is not shown again until every eligible photo has been shown once; then the cycle starts over.
- Fixed between refreshes, never regrouped live.

#### Multi-select
- Long-press a photo in the grid, an album, search results, or the Hidden album to enter selection mode; tap more photos to add them.
- "Select all" in albums and the Hidden album.
- Actions: Add to album, Share, Hide (Unhide in the Hidden album), Trash, and Remove from album (user albums only).

#### Background work
- Default albums are database queries over existing flags. They need no grouping job.
- `AlbumRotationWorker` runs every 2 days, without a charging requirement, since it only reads the database and picks photos (under 1 second at 50,000 photos, and it never loads the AI models). It publishes the next rotating album and refreshes Rediscover. A missed turn runs once when the phone next allows it; turns never pile up.

---

## 5. Non-Functional & Technical Requirements

### 5.1 Device Targets
- **Minimum:** Android 10 (API 29), 3 GB RAM.
- **Oldest supported:** 2018 phones updated to Android 10 (for example Galaxy S9, Pixel 3, Galaxy A7 2018, Nokia 6.1). Older phones that reached Android 10 (such as the 2016 Pixel) can still install the app but are not tested.
- **Reference test devices:** Low (a 2018 phone on Android 10 with 3 to 4 GB RAM, run in Firebase Test Lab or borrowed, since the only owned phone is the Realme; see `EXECUTION_PLAN.md` task T5.1), Mid (owned: Realme 8 5G, 8 GB RAM, MediaTek Dimensity 700), High (recent Pixel; Firebase Test Lab or borrowed).
- **Scope:** Photos only. Videos appear in the gallery grid but are not AI-indexed in v1.

### 5.2 Performance Targets (to be validated in Phase 0)
| Metric | Target (mid-tier device) |
| :--- | :--- |
| Search, query typed to results shown, 50,000 photos | under 300 ms (vector scan alone under 50 ms) |
| Indexing throughput (decode + all tiers) | at least 5 photos per second |
| Full index of 50,000 photos | within 3 nights of charging |
| Total download size (app + model pack) | under 100 MB |
| Embedding memory in RAM, 50,000 photos | about 25 MB |
| Preparing and posting one reminder (4.6), 50,000 photos | under 1 s; never loads the search index or AI models |

### 5.3 Background Processing, Battery & Heat
- Indexing runs through WorkManager with `setRequiresCharging(true)` and `setRequiresDeviceIdle(true)`.
- Work is split into batches (for example 200 photos) and saves progress after each batch, because the system can stop a worker after about 10 minutes.
- **Heat:** Before each batch, check `PowerManager.getCurrentThermalStatus()`. Pause at `THERMAL_STATUS_MODERATE` or higher and resume when it drops.
- **"Index now":** An optional user-started button that runs indexing in a foreground service with a visible notification (type `mediaProcessing` on Android 15+, `dataSync` below).
- **Background reliability:** Scheduled work (reminders, indexing, album rotation) keeps running when the app is closed, except after a force stop. Two things can still stop it: phone makers' own battery savers (Realme, Oppo, Xiaomi, Samsung, and others can freeze closed apps) and Android pausing apps that have not been used for a few months (Android 11+, which also removes permissions). The app:
  - Detects background restrictions, a rarely-used standby group, and "Pause app activity if unused", and shows a card explaining how to fix it, with a button to the right settings page.
  - Detects missed work (the daily reminder plan not running for 2 days, or indexing stalled for 3 days) as a sign of phone-maker blocking, which Android does not report directly.
  - Shows short built-in steps for common phone makers. Nothing is loaded from the web.
  - Never requests `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`. Google Play restricts it to specific app types, and a gallery likely does not qualify.

### 5.4 Privacy Enforcement
- `INTERNET` and `ACCESS_NETWORK_STATE` are removed from the final merged manifest with `tools:node="remove"`. This also blocks any library telemetry (such as ML Kit's usage reporting), since the app process cannot open network connections.
- A CI check fails the build if the merged manifest contains either permission.
- `android:allowBackup="false"`. Otherwise Android's automatic backup would copy the database (faces, places, search index) to Google Drive.
- Labels, person names, and settings can be exported to a local file that the user picks. This includes user albums (title, cover, photos), removed default albums, and the rotating album history, so suggestions do not repeat on a new phone.

### 5.5 Permissions & Store Policy
| Permission | Why | Notes |
| :--- | :--- | :--- |
| `READ_EXTERNAL_STORAGE` (Android 10 to 12) | Read photos | |
| `READ_MEDIA_IMAGES` (Android 13+) | Read photos | Requires Play Console "Photo and Video Permissions" declaration; a gallery app qualifies |
| `READ_MEDIA_VISUAL_USER_SELECTED` (Android 14+) | Partial access | If the user picks "Select photos", the app works on that subset and shows a banner explaining what is limited |
| `ACCESS_MEDIA_LOCATION` | GPS data for trips | Without it, Android strips location from photos |
| `USE_BIOMETRIC` | Vault | |
| `POST_NOTIFICATIONS` (Android 13+) | Memory reminders (4.6) | Requested in context, never during onboarding. Denied means no reminders; the app works otherwise |

### 5.6 First-Run Experience
Smart features cannot wait days for a full index. Indexing runs in this order:
1. On day one: "On This Day" and the widget work immediately from dates. Screenshots are filtered by path.
2. First: "On This Day" candidates for the next 7 days, so they get full junk filtering quickly.
3. Next: the last 90 days of photos (most likely to be searched).
4. Then: everything else, newest first.

Onboarding explains that search improves overnight while charging, and offers "Index now".

During the first library scan, photos appear in the grid as they are found, with a progress bar. If there are no memories for today, the Memories tab says "No memories from this day yet. Your photos from today will be here next year." and offers to open the camera.

While analysis runs, a "Getting to know your photos" card on Memories and Search shows progress, what is ready, and what is coming, with an "Index now" button. The first time analysis finishes, one "Your photos are ready" notification is sent (between 9 AM and 9 PM, counting as that day's memory reminder). The Trips chip and People tab explain what they are waiting for instead of showing nothing.

### 5.7 Quality Targets
Measured on an internal labeled set (about 2,000 photos, 50 test queries).
| Feature | Target |
| :--- | :--- |
| Junk filter precision (photos marked junk that really are junk) | at least 95% |
| Junk filter recall (junk that gets caught) | at least 80% |
| Search: a relevant photo in the top 5 results | at least 80% of queries |
| Face clusters containing only one person | at least 95% |
| Theme reminder lines naming the right theme (4.6) | at least 90% |
| Chat screenshot detection precision (screenshots in Chat screenshots that really are chats, 4.7) | at least 90% |

Precision is prioritized over recall: wrongly hiding a real memory is worse than letting a receipt through.

### 5.8 Success Metrics
The app has no network access, so it cannot send analytics. Success is measured with:
- Google Play Console vitals (crashes, ANRs, battery use), which Android reports at the system level: crash-free sessions at least 99.5%.
- Store rating and reviews: target 4.3 or higher.
- Opt-in feedback through an email intent the user sends themselves.

### 5.9 Data Model (initial)
- `photo`: media id, date taken, lat/lon, size, junk flag and reason, hidden flag, index version.
- `embedding`: photo id, INT8 vector (BLOB), model version.
- `face`: photo id, bounding box, face vector, person id.
- `person`: name, cover face, user-confirmed flag.
- `event`: start, end, place name, trip flag, cover photo.
- `exclusion_rule`: type (folder, date range, person), value.
- `photo` also gets: chat screenshot flag, pet flag (so album jobs never load AI models), and the Rediscover cycle in which it was last shown.
- `album`: kind (user, rotating, Rediscover), title, cover photo, created time, published time, candidate key (rotating only).
- `album_photo`: album id, photo id, position, added time.
- `rotation_history`: candidate key, published time. Kept permanently, even after the album is removed.
- Removed default albums are a settings value, not a table.

---

## 6. Phased Roadmap

┌────────────────────────────────────────────────────────────────────────┐
│ Phase 0: Technical Spikes (prove the risky parts first)                │
│ - MobileCLIP-S0 throughput and search latency on a 3 GB device.        │
│ - Merged manifest with no INTERNET, ML Kit bundled models working.     │
│ - Glance widget image memory limits with a 5-photo carousel.           │
│ Exit: the targets in 5.2 are met, or are revised with real numbers.    │
└───────────────────────────────────┬────────────────────────────────────┘
                                    │
                                    ▼
┌────────────────────────────────────────────────────────────────────────┐
│ Phase 1: MVP (Local Gallery, On This Day, Basic Widget)                │
│ - Gallery basics (4.1), permissions and partial access (5.5).          │
│ - MediaStore index, "On This Day", screenshot filter, Hide flag.       │
│ - Basic Daily Memory widget with Tap-to-Story.                         │
│ - Memory and capture reminders, at most two a day (4.6).               │
│ - Privacy enforcement: manifest check, allowBackup off (5.4).          │
│ - Albums tab: user albums, Favorites, Screenshots by path (4.7).       │
│ - Multi-select with Select all, Add to album, Share, Hide, Trash.      │
│ - Core logic in a plain Kotlin module (iOS-ready, see section 12).     │
└───────────────────────────────────┬────────────────────────────────────┘
                                    │
                                    ▼
┌────────────────────────────────────────────────────────────────────────┐
│ Phase 2: On-Device AI & Search                                         │
│ - Tier 1 junk filter (blur, OCR receipts and documents).               │
│ - Tier 2 CLIP embeddings, natural language search, category chips.     │
│ - WorkManager pipeline, thermal pause, first-run ordering, Index now.  │
│ - Model asset pack and model versioning.                               │
│ - Junk albums: Chat screenshots, Receipts & documents, Blurry.         │
└───────────────────────────────────┬────────────────────────────────────┘
                                    │
                                    ▼
┌────────────────────────────────────────────────────────────────────────┐
│ Phase 3: Smarter Memories & People                                     │
│ - Events and trips with offline place names.                           │
│ - Tier 3 face clustering and People Hub; pets via CLIP.                │
│ - Exclusion rules.                                                     │
│ - Rotating albums (5 max, 1 new every 2 days) and Rediscover.          │
└───────────────────────────────────┬────────────────────────────────────┘
                                    │
                                    ▼
┌────────────────────────────────────────────────────────────────────────┐
│ Phase 4: Widget Studio & Vault                                         │
│ - Widget Studio: sources, refresh, Material You styling.               │
│ - Encrypted Sensitive Vault with export.                               │
└───────────────────────────────────┬────────────────────────────────────┘
                                    │
                                    ▼
┌────────────────────────────────────────────────────────────────────────┐
│ Phase 5: Optimization & Polish                                         │
│ - Low-end device tuning, accessibility audit, UI localization.         │
│ - Quality target review against the labeled set (5.7).                 │
└────────────────────────────────────────────────────────────────────────┘

---

## 7. Out of Scope for v1
- Video indexing (videos are viewable only).
- NAS / SMB network storage (would require the network permission).
- Non-English search queries.
- Recognizing individual pets.
- Built-in photo editor.
- Cloud backup or sync of any kind.
- iPhone app (planned later, see section 12).

## 7a. Later (not in v1)
New ideas raised after the v1 scope was committed go here. Nothing moves from this list into v1 without the owner's explicit approval, recorded in the Decisions log.

(empty)

## 8. Risks & Open Questions
- **Indexing speed on 3 GB devices** may miss the 5 photos per second target. Fallback: skip Tier 3 on low-end devices or index at a smaller image size. Answered in Phase 0.
- **Device idle is rare on some phones** (users who charge while using the phone). The "Index now" button covers this; watch reviews for "search doesn't work" complaints.
- **MobileCLIP weights license** must be confirmed for commercial use. Fallback: an openly licensed CLIP variant of similar size.
- **Search quality on personal photos** may differ from benchmarks. Measured in Phase 2 against the labeled set.

## 9. Deferred: Monetization
Pricing and the free versus paid split will be decided later. Requirement to carry forward: any purchase check must work without the app having network access.

---

## 10. Testing Strategy

### 10.1 Test Data
- Tests use a **synthetic photo library only**: generated images, stock photos with permissive licenses, and staged photos of team members who consent. No real user photos, ever.
- The library includes EXIF dates spanning several years, GPS data for trips, screenshots, receipts, blurry shots, and repeated faces.
- The labeled quality set (5.7) lives in the repo with its labels, so anyone can rerun the quality numbers.

### 10.2 Test Levels
| Level | What it covers | Tools | When it runs |
| :--- | :--- | :--- | :--- |
| Logic (unit) | Junk rules, event and trip grouping, face clustering, search ranking, exclusion rules | JUnit on the plain Kotlin core module (no phone needed) | Every commit |
| On-device | MediaStore reading, permission states, background indexing, database upgrades | AndroidX Test, WorkManager `work-testing`, Room `MigrationTestHelper` | Every pull request |
| UI | Gallery, search, People Hub, onboarding, vault flows | Compose UI tests | Every pull request |
| Widgets | Widget layout and content per source and size | `glance-appwidget-testing` plus manual checks | Every pull request (automated), each release (manual) |
| AI quality | Junk precision and recall, search top-5, face cluster purity (5.7) | Evaluation script over the labeled set | Every model or pipeline change, and each release |
| Performance | Startup, scroll smoothness, search latency, indexing throughput (5.2) | Macrobenchmark, Microbenchmark | Nightly on reference devices, and each release |

### 10.3 Permission Scenarios
Every on-device and UI suite runs in all three photo access states:
1. Full access.
2. Partial access ("Select photos", Android 14+): the app works on the chosen photos and shows the limited-access banner.
3. Denied: the app shows a clear explanation screen and does not crash.

Plus: location permission denied (trips fall back to date-only events), and notifications denied (no reminders are sent, the Settings switches explain how to allow them, and nothing else changes).

### 10.4 Privacy Tests (release blockers)
- The merged manifest contains no `INTERNET` or `ACCESS_NETWORK_STATE` (5.4).
- An on-device test tries to open a network connection from the app and expects it to fail.
- `allowBackup` is false in the release build.
- No log statements print file paths, place names, or person names in release builds.

### 10.5 Battery & Heat
- An overnight indexing run on each reference device, checking battery drain in Android's battery stats, plus pause and resume on heat.
- A forced-heat test (simulated thermal status) confirms indexing pauses at `THERMAL_STATUS_MODERATE`.

### 10.6 Vault Tests
- Encrypt then decrypt returns the identical file.
- "Export vault back to gallery" restores every file.
- Adding or removing a fingerprint does not make the vault unreadable.
- Vault photos never appear in search, memories, or widgets.

### 10.7 Device & Launcher Matrix
- **Phones:** the low, mid, and high reference devices (5.1). Android 10, 13, 14, and the latest release are covered by emulators where the physical phones cannot run them.
- **Home screen apps:** Pixel Launcher, Samsung One UI Home, and one third-party launcher (such as Nova), since widgets render differently on each.

### 10.8 Accessibility
- TalkBack (screen reader) walkthrough of the main flows.
- Android Accessibility Scanner on every screen: touch targets, contrast, and labels.
- Large font and display size settings do not break layouts or widgets.

---

## 11. Release Plan

### 11.1 Release Stages
1. **Internal testing:** you plus a few trusted people, through Play's internal testing track. Installs within minutes, so it suits quick iteration.
2. **Closed beta (required):** because the developer account is a personal account, Google requires a closed test with **at least 12 testers opted in for 14 continuous days** before the app can go public. Recruit about 20 testers so drop-offs don't reset the clock.
3. **Production, staged rollout:** 10% of users, then 50%, then 100%, with at least 2 days at each step. Pause the rollout if crash-free sessions fall below 99.5% or new reviews report data or battery problems.

Play cannot roll an update back. A bad release is fixed by pausing the rollout and shipping a hotfix with a higher version number.

### 11.2 Release Gates
A build ships only when all of these pass:
- All automated tests (10.2) and all privacy tests (10.4).
- Performance targets (5.2) on the reference devices.
- AI quality targets (5.7), from Phase 2 onward.
- Crash-free sessions at least 99.5% during the beta stage.
- No open bugs that can lose or expose user photos.

### 11.3 Play Store Requirements Checklist
- [ ] **Data safety form:** declares no data collected and none shared. It must stay true; the privacy tests (10.4) back it up.
- [ ] **Photo and Video Permissions declaration** for `READ_MEDIA_IMAGES` (gallery is the core function).
- [ ] **Privacy policy page:** required even when nothing is collected. Host it in the public repo (for example via GitHub Pages) and state plainly that no data leaves the device.
- [ ] **Content rating questionnaire.**
- [ ] **Target API level:** meet Play's current requirement (within about one year of the latest Android release).
- [ ] **Store listing:** screenshots, a description whose privacy claims match the Data safety form, and a link to the public repo, described as "source available for inspection" (never "open source").

### 11.4 Signing & Versioning
- Use **Play App Signing**: Google holds the key that signs the app for users; you keep an **upload key**, backed up offline in two places. Losing the upload key is recoverable through Google support; leaking it is a security incident.
- Version numbers follow `MAJOR.MINOR.PATCH` (for example 1.2.0). The internal build number (`versionCode`) increases with every upload.
- Each release gets a git tag and release notes.
- Release builds use R8 code shrinking. Models ship in the install-time asset pack (3.2).

### 11.5 Public Repository
The code is published as a public repository so anyone can inspect it and verify the privacy claims. It is source-available, not open source.
- **License:** PolyForm Strict 1.0.0. Anyone may read the code and build it for their own noncommercial use (for example to check the privacy claims). Changing it, redistributing it, or publishing apps built from it is not allowed. GitHub's terms still allow forks on GitHub itself; the license governs everything else.
- **Contributions:** not accepted for now. If that changes, contribution terms must let the owner keep the license.
- **No secrets in git:** signing keys, keystore passwords, and Play credentials live outside the repo. A secret scanner runs on every commit.
- **Model files are not committed.** The build downloads them from their official source and checks a checksum. This keeps the repo small and respects the model licenses.
- **Attribution:** a `NOTICE` file and an in-app "Licenses" screen credit GeoNames (CC BY 4.0), MobileCLIP, MobileFaceNet, ML Kit, and the open-source libraries used. Every third-party license must allow use in a closed, commercial app with attribution (checked in plan task T0.4).
- **README:** explains the privacy design and how to verify it yourself (inspect the merged manifest, run the privacy tests).

### 11.6 After Launch
- Check Play Console vitals (crashes, ANRs, battery) and new reviews daily for the first two weeks after each release, then weekly.
- Bug reports come through GitHub issues and the opt-in feedback email (5.8).
- **Hotfix process:** fix on a branch, run the release gates, ship to internal testing, then a fast staged rollout (20%, then 100%).
- Model updates ship only inside app updates and trigger background re-indexing (3.2).

---

## 12. Future: iOS

Android ships first. The Android build is structured so an iPhone app can follow without a rewrite of the core logic.

### 12.1 Rule for Now
All core logic (junk rules, event and trip grouping, face clustering, search ranking, data model) lives in a **plain Kotlin module with no Android dependencies**. This module can later be shared with an iPhone app through **Kotlin Multiplatform** (a tool for sharing Kotlin code between Android and iOS).

### 12.2 What Carries Over vs. What Gets Rewritten
| Carries over | Rewritten for iPhone |
| :--- | :--- |
| Core logic module (via Kotlin Multiplatform) | UI (SwiftUI) |
| AI models (MobileCLIP is an Apple model with Core ML versions) | Photo access (PhotoKit) |
| Database design (Room supports Kotlin Multiplatform) | Widgets (WidgetKit, SwiftUI only) |
| Labeled quality set and targets | Background indexing (`BGProcessingTask`, runs only when iOS allows) |
| Product design and memory rules | Vault (Keychain, Face ID / Touch ID) |

### 12.3 Things to Know Before Committing
- **Weaker differentiation:** Apple Photos already does Memories, face recognition, and natural language search on the device. On iPhone, the main edge is the customizable widgets and memory rules.
- **No network permission to remove:** iOS has no equivalent of removing `INTERNET`. The privacy claim rests on Apple's "Data Not Collected" privacy label and the public repository.
- **Cost:** the Apple Developer Program is $99 per year, and building requires a Mac.
- **Background limits:** iOS gives less background time than Android, so initial indexing will take longer; the first-run ordering (5.6) matters even more.

---

## 13. Spec Revision Tracker

Plan for turning the original PRD into a buildable spec.

- [x] 1. Defer monetization (section 9; pricing removed from roadmap).
- [x] 2. Fix contradictions
  - [x] Remove Wi-Fi constraint from pipeline
  - [x] Fix Tier 1 / Tier 3 labels
  - [x] Remove duplicate Material You item
  - [x] Drop NAS / SMB (section 7)
  - [x] Add merged-manifest network check (5.4)
- [x] 3. Correct technical claims
  - [x] Face recognition model added; pets via CLIP (3.1 Tier 3)
  - [x] In-memory INT8 vector search; end-to-end latency at 50,000 photos (3.1 Tier 2, 5.2)
  - [x] Thermal status API (5.3)
  - [x] Remove "every unlock" refresh (4.3)
  - [x] ACCESS_MEDIA_LOCATION and offline place names (4.2)
  - [x] Widget image limits (4.3)
  - [x] Vault storage, encryption, data loss warning, Class 3 biometrics (4.5)
- [x] 4. Add missing sections
  - [x] Permissions and store policy (5.5)
  - [x] First-run indexing (5.6)
  - [x] Quality targets (5.7) and success metrics (5.8)
  - [x] Scope: photos only, English-only search (5.1, 3.1, 7)
  - [x] Model upgrades (3.2) and data model (5.9)
  - [x] Gallery basics (4.1)
- [x] 5. Rework roadmap with every feature assigned and a Phase 0 spike (section 6)
- [x] 6. Apply user decisions: local-only privacy pitch (persona renamed, bundled models), NAS dropped, Android 10 minimum, photos only
- [x] 7. Add Testing Strategy (section 10)
  - [x] Test data, levels, permission scenarios, privacy blockers, battery, vault, device matrix, accessibility
  - [x] Vault key must survive fingerprint changes (4.5, 10.6)
- [x] 8. Add Release Plan (section 11)
  - [x] Stages incl. 12 testers / 14 days closed test for personal accounts
  - [x] Release gates, Play checklist, signing and versioning, after-launch process
  - [x] Public repository: source-available (PolyForm Strict 1.0.0, replacing GPLv3), no secrets, models not committed, attribution
- [x] 9. Add Future: iOS (section 12) and plain Kotlin core module rule (Phase 1, section 7)
- [x] 10. Add Albums (4.7)
  - [x] User, Default, and Rotating albums; Rediscover; multi-select (4.7)
  - [x] Widget source (4.3), label backup (5.4), chat screenshot target (5.7), data model (5.9), roadmap (6)
  - [x] Reviewer check of 4.7 and answers to its open questions
  - [x] Execution plan tasks and tracker (`EXECUTION_PLAN.md`: T1.14, T1.15, T2.10, T3.10, T3.11, plus changes to T1.3, T1.4, T1.7, T1.9, T2.7, T2.8, T3.8, T4.1), feature list (`FEATURES.md` Feature 13)

Decisions log:
- Pitch is "private because local only", not "works without Google". Play Store distribution is fine; models are still bundled so no data goes through Google services.
- NAS / SMB dropped to keep the zero-network promise.
- Minimum Android 10 (API 29).
- Photos only for v1.
- Support floor: 2018 phones on Android 10. The minimum stays Android 10 (API 29); older phones on Android 10 may install but are untested. The owned Realme 8 5G is the main test phone; low-end checks use a 2018 phone in Firebase Test Lab or a borrowed one.
- Memory reminders (4.6) are in v1: local notifications only, at most two a day at random daytime times, with a bank of varied lines. Date and nudge lines ship in Phase 1, theme lines in Phase 2, and place lines in Phase 3.
- Personal Play developer account (closed test rule applies).
- Public repository under PolyForm Strict 1.0.0 (source-available, inspection and personal builds only; no redistribution or modified apps). Replaces the earlier GPLv3 choice, which would also have conflicted with closed-source ML Kit.
- Android first; iOS later, sharing core logic via Kotlin Multiplatform.
- Package ID: `dev.build_with_mhonn.memories` (from build-with-mhonn.dev). App name: "Memories" under the launcher icon; Play Store title "Memories - Gallery App" (22 characters).
- Owner and contact: Mhonn Czedrick Ybañez, mhonn.ybanez@gmail.com (copyright notice, in-app feedback, privacy policy). The email is public. Commits use the GitHub no-reply address.
- Repository: https://github.com/mhonnczedz1/memories_gallery_app
- Real-device testing: one phone, Realme 8 5G (8 GB RAM), used as the Mid reference device.
- Albums (4.7), user decisions: three groups (User, Default, Rotating); On This Day is not an album; rotating albums hold 5 at a time, one new every 2 days, published at 30+ photos, capped at 50, and frozen once published; pinning keeps a rotating album; default albums can be removed by the user; Rediscover is a default album of 50 ungrouped photos refreshed every 2 days; junk albums support Select all for cleanup; user albums show everything the user added, with exclusion rules applied only when used as a widget; bulk Trash on Android 10 is capped at 20 photos per action, with no extra storage permission.
- Albums (4.7), assistant calls for review: "oldest" means published longest ago; no repeats of a candidate; pinned albums become user albums; removed default albums are restored from Settings; Favorites is a default album; person and pet candidates are per calendar year and need a named person; the 50 are spread across time with favorites and faces first; Rediscover avoids repeats until every eligible photo is shown; exclusion rules apply to rotating albums, Rediscover, and widgets, never inside user albums; album membership survives trash and partial access, removed only on permanent delete or vault; ordinary events never count as grouped for Rediscover; user albums, multi-select, Favorites, and Screenshots in Phase 1, junk albums in Phase 2, rotating albums and Rediscover in Phase 3.
- Background reliability (5.3): the app detects and explains background limits instead of requesting a battery optimization exemption. Phone-maker help text is verified on the Realme; other brands are marked to verify.
- Estimates (to revisit after Phase 0, not commitments): about 8 to 12 weeks from start to release with a coding agent doing the development and the human handling device checks, testers, and store forms. The 14-day closed test and Play's production access review are the fixed waits. Agent usage is roughly 0.5 to 1 billion input tokens (mostly cache reads) and 5 to 15 million output tokens, about $500 to $1,500 on Opus 5.5 or $300 to $900 on Sonnet 5.5 at API list prices (cached 2026-10-06). Rework from failing device checks is the main cost driver.
- The execution plan lives in `EXECUTION_PLAN.md`; its tracker is section E9 there. The user-facing feature list lives in `FEATURES.md` and must always match the plan (checked by `tools/check-features.py`).
- Albums plan (assistant calls for review): photos moved to the system trash are kept as unavailable rather than deleted, so they return to their albums when restored; the widget source for phone folders is renamed `Folder` and user albums get their own `UserAlbum` source; chat screenshot and pet flags are computed at indexing time from the stored image fingerprint; suggestion keys use names and dates (not row ids) so they survive rebuilds and a new phone; Albums tab sections are titled "Your albums", "Collections", and "Suggested for you"; sharing is capped at 100 photos at a time.
