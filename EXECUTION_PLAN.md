# Memories: Execution Plan

> **For agentic workers:** REQUIRED SUB-SKILL: use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task by task. Tasks use checkbox (`- [ ]`) syntax in the tracker (E9).

**Goal:** Build and release the Android app specified in `PRD.md`, phase by phase, with every task independently verifiable.

**Architecture:** A multi-module Android project. All product logic (dates, junk rules, grouping, clustering, vector search, exclusion rules) lives in `:core`, a plain Kotlin module with no Android dependencies, so it is unit-testable on a computer and shareable with iOS later. Android-specific modules wrap MediaStore and Room (`:data`), on-device models (`:ml`), background indexing and services (`:engine`), widgets (`:widget`), encryption (`:vault`), and UI (`:app`).

**Tech Stack:** Kotlin 2.x, Gradle Kotlin DSL with a version catalog, Jetpack Compose (Material 3), Jetpack Glance, Room, DataStore, Hilt, WorkManager, Paging 3, Coil 3, kotlinx-coroutines, kotlinx-datetime, kotlinx-serialization, LiteRT, ML Kit bundled Text Recognition (Latin) and Face Detection, AboutLibraries, Spotless with ktlint, AndroidX Test, Gradle Managed Devices, Macrobenchmark and Microbenchmark, GitHub Actions, gitleaks.

**Spec:** [`PRD.md`](PRD.md). Section references like "(5.4)" point to the PRD. The Decisions log is PRD section 13.

---

## E1. Global Constraints

Every task implicitly includes all of these. Values are fixed; do not change them without a human decision recorded in the Decisions log (PRD.md section 13).

1. **Application ID:** `dev.build_with_mhonn.memories`. App name: `Memories` (launcher label); Play Store title `Memories - Gallery App`.
2. **Module namespaces:** `dev.build_with_mhonn.memories.<module>` (for example `dev.build_with_mhonn.memories.core`). The `:app` namespace equals the application ID.
3. **minSdk:** 29 (Android 10). **targetSdk and compileSdk:** the latest stable API level at the time of T0.2, and never below Google Play's current target requirement. Record the exact number in the Decisions log.
4. **JDK:** 17. **Kotlin:** latest stable 2.x at T0.2. **AGP:** latest stable at T0.2. All versions live only in `gradle/libs.versions.toml`.
5. **No network:** the release merged manifest must not contain `android.permission.INTERNET` or `android.permission.ACCESS_NETWORK_STATE` (5.4). Enforced by `:app:verifyPrivacy` (T0.3).
6. **Backup off:** `android:allowBackup="false"` and data extraction rules that exclude everything (5.4).
7. **Dependency denylist:** no artifact whose group or name matches `firebase-analytics`, `firebase-crashlytics`, `play-services-ads`, `play-services-measurement`, `play-services-analytics`, or any ad or analytics SDK. Enforced by `:app:verifyPrivacy`.
8. **Models:** bundled only, INT8 quantized, delivered in the install-time asset pack `models_pack` (3.2). Model binaries are never committed to git (11.5).
9. **Photos only:** videos appear in the grid but are never indexed (5.1).
10. **`:core` purity:** `:core` may depend only on Kotlin stdlib, kotlinx-coroutines-core, kotlinx-datetime, and kotlinx-serialization. No `android.*`, no `java.time`, no `java.io.File` in public signatures (12.1).
11. **Strings:** every user-visible string lives in `res/values/strings.xml`. Lint `HardcodedText` and `SetTextI18n` are errors.
12. **Logging:** release builds must not log file paths, display names, place names, person names, or search queries (10.4). Use a single `Log` wrapper in `:core` (`AppLog`) whose release implementation drops `debug` and `info`.
13. **Performance targets (5.2):** search end to end under 300 ms and vector scan under 50 ms at 50,000 photos; indexing at least 5 photos per second; full index of 50,000 photos within 3 nights; total download under 100 MB; embedding RAM about 25 MB at 50,000 photos. Measured on the Mid device (Realme 8 5G).
14. **Quality targets (5.7):** junk precision at least 95%, junk recall at least 80%, search top-5 at least 80% of queries, face cluster purity at least 95%, chat screenshot precision at least 90%.
15. **Copy rules:** no em dashes or en dashes in user-facing strings.
16. **License:** PolyForm Strict 1.0.0 for all source (source-available, not open source). Never call the project "open source" in docs, the README, or the store listing. Third-party assets keep their own licenses, listed in `NOTICE`.

---

## E2. Agent Operating Rules

### E2.1 Before starting a task
1. Read E1, E4, E5, the task, and every PRD section the task cites.
2. Confirm every task in the task's **Depends on** list is marked `[x]` in E9. If not, stop.
3. Work on a branch named `task/<ID>-<short-slug>` (for example `task/T1.4-mediastore-sync`), in its own git worktree.

### E2.2 Definition of done (every task)
A task is done only when all of these are true:
1. Every item under **Validation** passes, with the command output captured in the PR description.
2. `./gradlew spotlessCheck lint :app:verifyPrivacy :core:test testDebugUnitTest` passes (the "base check").
3. CI is green on the PR.
4. Public names and types match the task's **Produces** list and E5 exactly.
5. If the task adds, removes, or changes anything the user sees, `FEATURES.md` is updated in the same PR, and `python -I tools/check-features.py` passes.
6. The PR is merged to `main` with a squash commit titled `<ID>: <summary>` (for example `T1.4: MediaStore sync`), using Conventional Commit style in the body.

### E2.3 Tracker rule
- Task branches never edit `PRD.md` or `EXECUTION_PLAN.md`.
- The orchestrating agent (the session running this plan) marks the task `[x]` in E9 of `EXECUTION_PLAN.md` in its working copy, records any decisions in the PRD Decisions log, right after the merge, and adds a one-line result note.
- Any edit to this plan that adds, removes, or renames a task must update `FEATURES.md` in the same commit, so the feature list always matches the plan.
- The tracker is committed only when a phase gate (G0 to G5, GR) passes, in a commit titled `Phase <N> complete: tracker update`.

### E2.4 Never do these
1. Add any permission, dependency, or code path that opens network connections, or remove `:app:verifyPrivacy` from `check`.
2. Change a numeric target from E1 or the PRD. If a target cannot be met, stop and report (E2.5).
3. Skip, disable, `@Ignore`, or loosen a test to make CI pass.
4. Commit secrets, keystores, `local.properties`, model binaries, or real personal photos.
5. Change a public name or type that another task's **Produces** list defines. If one must change, stop and report.
6. Use `java.time` or Android APIs inside `:core`.

### E2.5 Stop and ask a human when
1. A validation step needs the physical phone or a human account (marked **HUMAN** or **DEVICE**). Set the task to `AWAITING HUMAN` in the PR description, finish everything else, and report exactly what the human must run and what output to send back.
2. A target is missed after the task's listed fallbacks are exhausted.
3. A license is unclear or forbids redistribution.
4. The task is ambiguous in a way that would change a public interface or user-visible behavior.

### E2.6 Commands an agent can rely on
| Purpose | Command | Pass looks like |
| :--- | :--- | :--- |
| Base check | `./gradlew spotlessCheck lint :app:verifyPrivacy :core:test testDebugUnitTest` | `BUILD SUCCESSFUL` |
| Instrumented tests on emulators | `./gradlew <module>:ciGroupDebugAndroidTest` | `BUILD SUCCESSFUL`, 0 failures in every device report |
| Instrumented tests on the phone | `./gradlew <module>:connectedDebugAndroidTest` (phone attached via `adb`) | `BUILD SUCCESSFUL` |
| Microbenchmarks on the phone | `./gradlew :microbenchmark:connectedReleaseAndroidTest` | JSON in `microbenchmark/build/outputs/connected_android_test_additional_output/` |
| Macrobenchmarks on the phone | `./gradlew :benchmark:connectedBenchmarkAndroidTest` | JSON in `benchmark/build/outputs/connected_android_test_additional_output/` |
| Release bundle | `./gradlew :app:bundleRelease` | `app/build/outputs/bundle/release/app-release.aab` exists |

---

## E3. Environment

- **Gradle Managed Devices** (defined in T0.2, used by every instrumented task):
  - `api29`: Pixel 6 profile, API 29, `aosp` image.
  - `api34`: Pixel 6 profile, API 34, `aosp-atd` image (needed for Android 14 partial photo access).
  - `apiLatest`: Pixel 6 profile, latest API from E1.3, `aosp-atd` image.
  - Group `ci` = all three.
- **Physical phone (DEVICE steps):** Realme 8 5G, 8 GB RAM, Dimensity 700. The human records its Android version in T0.1. It is the Mid reference device. Behaviors that need Android 14+ are validated on `api34` and `apiLatest` only.
- **Realme note:** Realme UI is aggressive about killing background work. Any DEVICE validation of background indexing must be run with default battery settings, not with optimizations disabled, because users will have defaults.
- **Python tooling:** `tools/` scripts use Python 3.11+ with pinned `requirements.txt` per tool and run inside a virtual environment. Run with `python -I`.

---

## E4. Module Map & Dependency Rules

| Module | Type | Owns | May depend on |
| :--- | :--- | :--- | :--- |
| `:core` | Kotlin JVM | Domain types, date rules, junk decision, memory selection, event and trip grouping, geo lookup, face clustering, vector math and index, tokenizer, exclusion rules, index ordering | Kotlin stdlib, kotlinx-coroutines-core, kotlinx-datetime, kotlinx-serialization |
| `:data` | Android library | Room database, DAOs, MediaStore reading and sync, photo access state, DataStore settings, label export and import | `:core` |
| `:ml` | Android library | Model registry, LiteRT runners (CLIP image, CLIP text, face embedding), image preprocessing, ML Kit wrappers | `:core` |
| `:engine` | Android library | WorkManager indexing pipeline, thermal guard, "Index now" foreground service, in-memory vector index holder, search, categories, memory assembly, face clustering jobs | `:core`, `:data`, `:ml` |
| `:widget` | Android library | Glance widgets, widget refresh workers, widget configuration screen | `:core`, `:data`, `:engine` |
| `:vault` | Android library | Keystore key, file encryption format, vault repository | `:core`, `:data` |
| `:app` | Android application | Activities, Compose screens, navigation, Hilt graph, onboarding, story viewer | all of the above |
| `:models_pack` | Asset pack (install-time) | Model files and manifest, downloaded at build time | none |
| `:testing` | Android library (test only) | `TestPhotoSeeder`, fixtures, fake clocks | `:core`, `:data` |
| `:microbenchmark` | Android library (benchmark) | Model and vector microbenchmarks | `:core`, `:ml`, `:testing` |
| `:benchmark` | Android test (macrobenchmark) | Startup, scroll, search macrobenchmarks | targets `:app` |
| `buildSrc` | Gradle build logic | `verifyPrivacy` task and its checker | none |

Rules:
1. Dependencies only flow in the directions above. `:core` depends on nothing project-internal.
2. Module-internal classes are `internal` unless listed in E5.
3. Each module has one Hilt module file `di/<Module>Module.kt` (not `:core`, which has no DI).

---

## E5. Shared Contracts

These names and types are used across tasks. The task listed in "Defined in" creates them; later tasks consume them unchanged. Types are Kotlin.

### E5.1 `:core` (package `dev.build_with_mhonn.memories.core`)
| Name | Shape | Defined in |
| :--- | :--- | :--- |
| `GeoPoint` | data class `(lat: Double, lon: Double)` | T1.1 |
| `JunkReason` | enum `SCREENSHOT, RECEIPT, DOCUMENT, BLURRY` (this order is also precedence) | T1.1 |
| `PhotoMeta` | data class `(mediaId: Long, relativePath: String?, displayName: String, mimeType: String, width: Int, height: Int, dateTakenUtcMillis: Long?, dateAddedUtcMillis: Long, sizeBytes: Long, location: GeoPoint?, isVideo: Boolean, isFavorite: Boolean, isHidden: Boolean, junkReason: JunkReason?, notJunkOverride: Boolean)` | T1.1 |
| `CaptureTime.effective(photo: PhotoMeta, nowUtcMillis: Long): Long?` | function | T1.1 |
| `ExclusionRule` | sealed interface: `Folder(relativePathPrefix: String)`, `DateRange(startUtcMillis: Long, endUtcMillis: Long)`, `Person(personId: Long)` | T1.1 |
| `Eligibility.forMemories(photo: PhotoMeta, rules: List<ExclusionRule>, personIdsInPhoto: Set<Long>, nowUtcMillis: Long): Boolean` | function | T1.1 |
| `OnThisDayGroup` | data class `(yearsAgo: Int, photos: List<PhotoMeta>)` | T1.2 |
| `OnThisDaySelector.select(photos: Sequence<PhotoMeta>, today: LocalDate, zone: TimeZone, nowUtcMillis: Long, windowDays: Int = 3): List<OnThisDayGroup>` | function | T1.2 |
| `MemoryPicker.pick(groups: List<OnThisDayGroup>, maxItems: Int = 5, seed: LocalDate): List<PhotoMeta>` | function | T1.2 |
| `ScreenshotRule.isScreenshot(photo: PhotoMeta, screenWidthPx: Int, screenHeightPx: Int): Boolean` | function | T1.6 |
| `EMBEDDING_DIM` | `const val = 512` | T2.4 |
| `QuantizedVector` | class `(bytes: ByteArray, scale: Float)`; `bytes.size` equals the source vector's dimension (`VectorIndex` requires `EMBEDDING_DIM`; face vectors use the face model's dimension) | T2.4 |
| `VectorQuantizer.quantize(v: FloatArray): QuantizedVector` / `VectorMath.l2Normalize(v: FloatArray): FloatArray` | functions | T2.4 |
| `ScoredMedia` | data class `(mediaId: Long, score: Float)` | T2.4 |
| `VectorIndex` | class: `put(mediaId: Long, v: QuantizedVector)`, `remove(mediaId: Long)`, `size: Int`, `search(query: FloatArray, topK: Int, allow: (Long) -> Boolean): List<ScoredMedia>` | T2.4 |
| `ClipTokenizer` | class `(vocab: List<String>, merges: List<String>)` with `encode(text: String): IntArray` (length 77) | T2.1 |
| `JunkSignals` | data class `(isScreenshot: Boolean, laplacianVariance: Double, clipDocumentScore: Float, clipReceiptScore: Float, ocrCharCount: Int?, ocrTextAreaRatio: Float?)` | T2.5 |
| `JunkThresholds` | data class with defaults, loaded from `core/src/main/resources/junk_thresholds.json` | T2.5 |
| `JunkClassifier.classify(s: JunkSignals, t: JunkThresholds): JunkReason?` | function | T2.5 |
| `LaplacianVariance.compute(gray: ByteArray, width: Int, height: Int): Double` | function | T2.5 |
| `IndexPriority.order(photos: List<PhotoMeta>, today: LocalDate, zone: TimeZone, nowUtcMillis: Long): List<Long>` | function | T2.6 |
| `City` | data class `(name: String, countryCode: String, admin1: String?, point: GeoPoint, population: Int)` | T3.1 |
| `CityIndex` | class loaded from bytes: `nearest(p: GeoPoint, maxKm: Double = 50.0): City?` | T3.1 |
| `EventCluster` | data class `(photoIds: List<Long>, startUtcMillis: Long, endUtcMillis: Long, medianLocation: GeoPoint?)` | T3.2 |
| `EventGrouper.group(photos: List<PhotoMeta>, nowUtcMillis: Long, gapMillis: Long = 3h): List<EventCluster>` | function | T3.2 |
| `HomeAreaEstimator.estimate(points: List<Pair<Long, GeoPoint>>, nowUtcMillis: Long): GeoPoint?` | function | T3.3 |
| `Trip` | data class `(events: List<EventCluster>, startUtcMillis: Long, endUtcMillis: Long, title: TripTitle)`; `TripTitle` is sealed: `City(name)`, `Country(code)`, `Countries(codes)` | T3.3 |
| `TripDetector.detect(events: List<EventCluster>, home: GeoPoint?, zone: TimeZone, cities: CityIndex): List<Trip>` | function | T3.3 |
| `FaceClusterer` | object: `assign(v: QuantizedVector, centroids: Map<Long, FloatArray>, threshold: Float): Long?`, `recluster(faces: List<FaceItem>, eps: Float, minPts: Int, constraints: ClusterConstraints): Map<Long, Long>` | T3.4 |
| `WidgetSource` | sealed interface: `OnThisDay`, `Person(personId: Long)`, `Pets`, `Favorites`, `Folder(relativePath: String)`, `UserAlbum(albumId: Long)` | T1.11 (only `OnThisDay`), T4.1 (rest) |
| `RefreshPeriod` | enum `HOURLY(1h), SIX_HOURS(6h), DAILY(24h)` | T1.11 |
| `ReminderKind` | enum `MEMORY, CAPTURE` | T1.13 |
| `ReminderFamily` | enum `PLACE, GET_TOGETHER, NATURE, FOOD, PETS, YEARS_AGO, MONTH_AGO, NUDGE` (this order is also precedence, NUDGE excluded) | T1.13 |
| `ReminderLine` | data class `(id: String, family: ReminderFamily, titleRes: String, bodyRes: String, allowedFrom: LocalTime?, allowedUntil: LocalTime?, sensitive: Boolean)`; `sensitive` lines use the neutral lock screen text | T1.13 |
| `ReminderCandidate` | data class `(family: ReminderFamily, score: Float, yearsAgo: Int?, placeName: String?, memoryType: String, memoryKey: String, coverMediaId: Long)` | T1.13 (date families), T2.9 (themes), T3.9 (place) |
| `ReminderCopy.choose(kind: ReminderKind, candidates: List<ReminderCandidate>, now: LocalTime, history: Map<String, LocalDate>, today: LocalDate, random: Random): Pair<ReminderLine, ReminderCandidate?>?` | function; the line bank is `ReminderCopy.LINES` | T1.13 |
| `MonthAgoSelector.select(photos: Sequence<PhotoMeta>, today: LocalDate, zone: TimeZone, nowUtcMillis: Long): List<PhotoMeta>` | function; photos from `today - 30 days`, plus or minus 1 day | T1.13 |
| `ReminderPlanner.slotsFor(date: LocalDate, zone: TimeZone, random: Random, windowStart: LocalTime = 09:00, windowEnd: LocalTime = 21:00, minGapMinutes: Int = 180): List<Pair<ReminderKind, Instant>>` | function | T1.13 |
| `AppLog` | interface `d/i/w/e(tag, msg)` plus `ReleaseLog` and `DebugLog` | T0.2 |
| `AlbumKind` | enum `USER, ROTATING, REDISCOVER` (stored in `album.kind`) | T1.15 |
| `DefaultAlbum` | enum `FAVORITES, SCREENSHOTS, CHAT_SCREENSHOTS, RECEIPTS_DOCUMENTS, BLURRY, REDISCOVER` (this order is also display order) | T1.15 |
| `JunkAlbumRule.albumFor(photo: PhotoMeta, isChatScreenshot: Boolean): DefaultAlbum?` | function; one of the four junk albums or `null` | T1.15 |
| `CandidateKind` | enum `TRIP, PERSON_YEAR, PETS_YEAR` (this order is also turn order) | T3.10 |
| `AlbumTitle` | sealed interface: `Trip(title: TripTitle, year: Int)`, `PersonYear(name: String, year: Int)`, `PetsYear(year: Int)`; `:app` formats it with string resources | T3.10 |
| `AlbumCandidate` | data class `(key: String, kind: CandidateKind, title: AlbumTitle, photos: List<PhotoMeta>, endUtcMillis: Long)`; `photos` are already eligibility-filtered | T3.10 |
| `AlbumCurator.pick(photos: List<PhotoMeta>, faceCounts: Map<Long, Int>, max: Int = 50): List<PhotoMeta>` | function | T3.10 |
| `RotationPlanner` | object: `next(candidates: List<AlbumCandidate>, publishedKeys: Set<String>, lastKind: CandidateKind?, nowUtcMillis: Long, random: Random): AlbumCandidate?`; constants `MIN_PHOTOS = 30`, `MAX_PHOTOS = 50`, `MAX_ACTIVE = 5`, `INTERVAL_DAYS = 2` | T3.10 |
| `RediscoverPicker.pick(pool: List<PhotoMeta>, lastShownCycle: Map<Long, Int>, cycle: Int, random: Random, max: Int = 50): RediscoverPick(photos: List<PhotoMeta>, cycle: Int)` | function | T3.11 |

### E5.2 Android modules
| Name | Module | Shape | Defined in |
| :--- | :--- | :--- | :--- |
| `PhotoAccessState` | `:data` | enum `FULL, PARTIAL, DENIED` | T1.5 |
| `PhotoAccessChecker` | `:data` | `current(): PhotoAccessState`, `hasMediaLocation(): Boolean` | T1.5 |
| `MemoriesDatabase` | `:data` | Room database, version starts at 1, schemas exported to `data/schemas/` | T1.3 |
| Entities and DAOs | `:data` | as listed in T1.3 | T1.3 |
| `MediaSyncer` | `:data` | `suspend fun sync(): SyncResult(added: Int, updated: Int, removed: Int)`, `progress: StateFlow<SyncProgress?>` | T1.4 |
| `PhotoRepository` | `:data` | `observeGrid(): Flow<PagingData<PhotoMeta>>`, `get(mediaId): PhotoMeta?`, `setHidden`, `setNotJunk`, `setLocalFavorite`, `allForMemories(): List<PhotoMeta>` | T1.4 |
| `ModelRegistry` | `:ml` | `get(name: String): ModelInfo` reading `models/manifest.json` from assets | T0.6 (minimal), T2.3 (final) |
| `ClipImageEncoder` | `:ml` | `embed(bitmap: Bitmap): FloatArray` (L2-normalized, length 512) | T0.6 (raw), T2.3 (final) |
| `ClipTextEncoder` | `:ml` | `embed(text: String): FloatArray` (L2-normalized, length 512) | T0.6 (raw), T2.3 (final) |
| `ImagePreprocessor` | `:ml` | `decodeForClip(uri: Uri): Bitmap?`, `decodeForAnalysis(uri: Uri, maxSide: Int): Bitmap?` | T2.2 |
| `TextDetector` | `:ml` | `suspend fun detect(bitmap: Bitmap): OcrSignals(charCount: Int, textAreaRatio: Float)` | T2.5 |
| `FaceDetectorWrapper` | `:ml` | `suspend fun detect(bitmap: Bitmap): List<DetectedFace(box: RectF, leftEye: PointF?, rightEye: PointF?)>` | T3.4 |
| `FaceEmbedder` | `:ml` | `embed(aligned112: Bitmap): FloatArray` (L2-normalized) | T3.4 |
| `IndexScheduler` | `:engine` | `ensureScheduled()`, `indexNow()`, `cancelIndexNow()` | T2.6 |
| `IndexStatus` | `:engine` | `StateFlow<IndexProgress(indexed: Int, total: Int, state: IndexState)>`, `IndexState` enum `IDLE, RUNNING, PAUSED_THERMAL, WAITING_FOR_CHARGER` | T2.6 |
| `ThermalGuard` | `:engine` | `shouldPause(): Boolean` | T2.6 |
| `EmbeddingIndexHolder` | `:engine` | singleton owning the `VectorIndex`, `suspend fun ensureLoaded()` | T2.7 |
| `SearchService` | `:engine` | `suspend fun search(query: String, limit: Int = 200): List<ScoredMedia>` | T2.7 |
| `CategoryService` | `:engine` | `suspend fun photosFor(category: Category): List<Long>`, `Category` enum `PETS, FOOD, DOCUMENTS, TRIPS, SCREENSHOTS` | T2.7 |
| `MemoryService` | `:engine` | `suspend fun today(): List<OnThisDayGroup>`, `suspend fun forWidget(source: WidgetSource, max: Int = 5): List<PhotoMeta>` | T1.10 |
| `ReminderSettings` | `:data` | DataStore: `memoryEnabled: Flow<Boolean>`, `captureEnabled: Flow<Boolean>` (both default true), `lastMemoryOpenedDate: Flow<LocalDate?>`, `lineHistory: Flow<Map<String, LocalDate>>` (line id to last used date, entries older than 14 days dropped), `lastPlanRunDate: Flow<LocalDate?>` (T1.16), setters for each | T1.13 |
| `BackgroundHealth` | `:engine` | `issues(): Flow<Set<HealthIssue>>`; `HealthIssue` enum `RESTRICTED, RARE, HIBERNATION, MISSED_REMINDERS, STALLED_INDEXING` | T1.16 (`STALLED_INDEXING` in T2.6) |
| `ReminderCandidateSource` | `:engine` | interface `suspend fun candidates(today: LocalDate): List<ReminderCandidate>`; Hilt multibinding set, one per family group | T1.13 (dates), T2.9 (themes), T3.9 (place) |
| `ReminderScheduler` | `:engine` | `ensureScheduled()`, `rescheduleToday()` | T1.13 |
| `AlbumRepository` | `:data` | `createUser(title: String): Long`, `rename(albumId, title)`, `setCover(albumId, mediaId?)`, `addPhotos(albumId, ids: List<Long>)`, `removePhotos(albumId, ids)`, `delete(albumId)`, `pin(albumId)`, `observeAlbums(): Flow<List<AlbumSummary>>`, `observePhotos(albumId): Flow<PagingData<PhotoMeta>>`, `observeDefault(album: DefaultAlbum): Flow<PagingData<PhotoMeta>>`, `defaultCounts(): Flow<Map<DefaultAlbum, Int>>`; `AlbumSummary(id: Long, kind: AlbumKind, title: String?, coverMediaId: Long?, count: Int, publishedUtc: Long?)` | T1.15 (user and default), T3.10 (rotating) |
| `AlbumSettings` | `:data` | DataStore: `removedDefaults: Flow<Set<DefaultAlbum>>`, `lastRotationKind: Flow<CandidateKind?>`, `rediscoverCycle: Flow<Int>` (default 1), setters for each | T1.15, T3.10, T3.11 |
| `SelectionActions` | `:app` | `share(ids): ActionRequest`, `hide(ids, hidden: Boolean)`, `trash(ids): ActionRequest`, `addToAlbum(ids, albumId)`, `removeFromAlbum(ids, albumId)`; constants `API29_TRASH_LIMIT = 20`, `SHARE_LIMIT = 100` | T1.14 (share, hide, trash), T1.15 (album actions) |
| `AlbumRotationWorker` | `:engine` | unique periodic work `album-rotation`, every `INTERVAL_DAYS`; `AlbumScheduler.ensureScheduled()` | T3.10 (rotating), T3.11 (adds Rediscover) |
| `VaultRepository` | `:vault` | `moveIn(ids: List<Long>)`, `exportAll()`, `observeItems()`, `openDecrypted(itemId): InputStream` | T4.3 |
| Deep link to story | `:app` | intent action `dev.build_with_mhonn.memories.OPEN_STORY` with extras `memory_type` (`on_this_day`, `month_ago`, `trip`, `event`, `widget`) and `memory_key` (String) | T1.10 |

---

## E6. Review Focus

The five failure modes most likely to hurt a real user that the feature tasks would not naturally test. Each has a pinned test in the owning task.

1. **Huge libraries (100,000 photos):** sync, memory use, and search must not degrade sharply. Expect sync to finish, the grid to scroll, and the vector scan to stay under 100 ms at 100,000. Pinned in T1.4 (sync at scale) and T2.4 (scan at 100,000).
2. **Bad or missing metadata:** no `DATE_TAKEN`, dates of 1970 or in the future, no GPS, zero-byte or corrupt files. Expect the photo to stay visible in the grid, be left out of date-based memories, and never crash indexing. Pinned in T1.1, T1.4, and T2.6.
3. **Interrupted indexing:** process killed, charger unplugged, or thermal pause mid-batch. Expect no duplicate or half-written rows and a clean resume. Pinned in T2.6.
4. **Photos disappearing under the app:** photo deleted in another app, partial access reduced, or permission revoked while a widget or story shows it. Expect the widget and story to skip to the next photo or show the empty state, never a crash or broken image. Albums keep the photo and show it again when it comes back (restored from trash, or reselected under partial access). Pinned in T1.4, T1.10, T1.11, and T1.15.
5. **Calendar edges:** Feb 29, the ±3 day window across New Year, daylight saving changes, and photos taken in another time zone. Expect "On This Day" to follow the rules in T1.2 exactly. Pinned in T1.2.

---

## E7. Human Checkpoints

| ID | When | What the human does | Output the agent needs back |
| :--- | :--- | :--- | :--- |
| H1 | T0.1 | Create the GitHub repo, install tools, enable USB debugging on the Realme | Confirmation, phone's Android version |
| H2 | T0.4 | Approve the model and data license review | "Approved" or a chosen fallback |
| H3 | T0.5 | Create the GitHub Release `models-v1` if the agent cannot use `gh` | Release URL |
| H4 | G0 and every DEVICE step | Run the listed command with the Realme attached, send back the output | JSON or console output |
| H5 | T2.8 | Review and correct labels in the labeled quality set | Updated `labels.json` |
| H6 | R1 (after G1) | Create the Play Console app, enable Play App Signing, generate and back up the upload key | Confirmation only. Never send the key. |
| H7 | R4 (after G2) | Recruit about 20 closed testers and start the 14-day closed test | Date started, tester count |
| H8 | R5 | Fill the Data safety form, photo permission declaration, content rating, store listing | Confirmation |
| H9 | T4.3 and G4 | Run the fingerprint enrollment change test on the Realme | Pass or fail |
| H10 | T5.1 | Provide a 2018 phone on Android 10 with 3 to 4 GB RAM (Firebase Test Lab or borrowed; the Realme cannot stand in for it) | Device model, year, and RAM |
| H11 | GR | Approve each staged rollout step | "Go" per step |

---

## E8. Phases & Tasks

Task format: **Depends on** (task IDs), **PRD** (PRD.md sections), **Touches** (modules and key files), **Produces** (names later tasks use; see E5), **Requirements** (numbered, exact), **Validation** (command and expected result; test cases are written as "given, expect"). A task is done per E2.2.

---

### Phase 0: Foundations & Technical Spikes

#### T0.1 Repository and workstation setup (HUMAN, H1)
- **Depends on:** none. **PRD:** 11.5.
- **Requirements:**
  1. Create the public GitHub repo `mhonnczedz1/memories_gallery_app` with no initial files.
  2. In the local folder that contains `PRD.md`, run `git init`, set `main` as the default branch, and add the remote `origin` pointing to the repo.
  3. In GitHub settings: enable secret scanning and push protection; protect `main` (require a PR and passing CI; no force pushes).
  4. Install Android Studio (latest stable), JDK 17, Python 3.11+, and the GitHub CLI (`gh`), and run `gh auth login`.
  5. On the Realme: enable Developer options and USB debugging. Record its Android version.
- **Validation:**
  - `git remote -v` shows `origin https://github.com/mhonnczedz1/memories_gallery_app.git`.
  - `adb devices` lists the Realme as `device`.
  - The Android version is recorded in the Decisions log.

#### T0.2 Project scaffold and CI
- **Depends on:** T0.1. **PRD:** 5.1, 5.4, 11.4, 11.5, 12.1.
- **Touches:** `settings.gradle.kts`, `build.gradle.kts`, `gradle/libs.versions.toml`, `gradle.properties`, all module folders from E4 (empty except a placeholder class each), `.gitignore`, `.editorconfig`, `LICENSE`, `NOTICE`, `README.md`, `.github/workflows/ci.yml`, `.github/workflows/instrumented.yml`, `.gitleaks.toml`, `core/.../AppLog.kt`, `tools/check-features.py`.
- **Produces:** the module layout in E4; `AppLog`; Gradle Managed Devices `api29`, `api34`, `apiLatest` and group `ci` (E3); `VERSION_NAME=0.1.0` and `VERSION_CODE=1` in `gradle.properties`.
- **Requirements:**
  1. All modules from E4 exist and are included in `settings.gradle.kts`. `:core` uses the `kotlin("jvm")` plugin. `:models_pack` uses `com.android.asset-pack` with `deliveryType = "install-time"` and pack name `models_pack`; `:app` declares it in `assetPacks`.
  2. Versions come only from `libs.versions.toml`. Record targetSdk, compileSdk, Kotlin, and AGP versions in the Decisions log.
  3. `:app` has applicationId `dev.build_with_mhonn.memories`, a launcher activity `MainActivity` showing the text "Memories" from `strings.xml`, and R8 enabled for release (`isMinifyEnabled = true`, `isShrinkResources = true`).
  4. Spotless with ktlint applies to all Kotlin and Gradle Kotlin files. Android Lint runs with `warningsAsErrors = false` but with `HardcodedText`, `SetTextI18n`, `MissingPermission`, and `UnusedResources` as errors.
  5. `.gitignore` covers: `local.properties`, `*.jks`, `*.keystore`, `keystore.properties`, `models_pack/src/main/assets/models/*.tflite`, `models_pack/src/main/assets/models/*.bin`, `/tools/**/.venv`, `build/`, `.gradle/`, `.idea/` (except shared code style), `*.aab`, `*.apk`.
  6. `LICENSE` is the full PolyForm Strict 1.0.0 text with `Required Notice: Copyright <year> Mhonn Czedrick Ybañez` (the year the scaffold is created). `NOTICE` lists third-party assets (empty table for now, with columns: asset, license, source URL). `README.md` has sections: About, Privacy design (placeholder), Building, License ("Source available for inspection under PolyForm Strict 1.0.0. Not open source. You may read and build it for personal, noncommercial use; you may not modify, redistribute, or publish it.").
  7. `AppLog`: `DebugLog` forwards to `println` in `:core` tests, and an Android implementation in `:app` forwards to `android.util.Log`; `ReleaseLog` drops `d` and `i`. Selection is by `BuildConfig.DEBUG` in `:app`.
  8. `ci.yml` runs on every push and PR: JDK 17 setup, Gradle cache, base check (E2.6), `:app:assembleRelease` (unsigned), and gitleaks.
  9. `instrumented.yml` runs on PRs to `main` and nightly: `./gradlew ciGroupDebugAndroidTest` for modules that have instrumented tests, with KVM enabled on the runner.
  10. `tools/check-features.py` (Python standard library only) reads the task IDs from the E9 checkboxes in `EXECUTION_PLAN.md` and every task ID mentioned in `FEATURES.md`. It fails, listing the IDs, if any plan task is missing from `FEATURES.md` or `FEATURES.md` mentions an ID not in the plan. `ci.yml` runs it on every push and PR.
- **Validation:**
  - `./gradlew spotlessCheck lint :core:test testDebugUnitTest :app:assembleDebug :app:assembleRelease` returns `BUILD SUCCESSFUL`.
  - `./gradlew :app:installDebug` on `api34` shows the "Memories" launcher entry.
  - CI is green on the first PR.
  - `gitleaks detect` reports no leaks.
  - `python -I tools/check-features.py` passes. Removing one task ID from a temporary copy of `FEATURES.md` makes it fail and name that ID.

#### T0.3 Privacy gates
- **Depends on:** T0.2. **PRD:** 5.4, 10.4.
- **Touches:** `buildSrc/` (checker plus task), `app/build.gradle.kts`, `app/src/main/AndroidManifest.xml`, `app/src/main/res/xml/data_extraction_rules.xml`, `app/src/main/res/xml/backup_rules.xml`, `app/src/androidTest/.../NetworkBlockedTest.kt`.
- **Produces:** Gradle task `:app:verifyPrivacy`, wired into `check` and the base check.
- **Requirements:**
  1. The app manifest declares `<uses-permission android:name="android.permission.INTERNET" tools:node="remove"/>` and the same for `ACCESS_NETWORK_STATE`.
  2. `android:allowBackup="false"`; `android:dataExtractionRules` points to rules excluding all domains for both cloud backup and device transfer; `android:fullBackupContent` points to rules excluding all.
  3. `verifyPrivacy` reads the **release** merged manifest (`processReleaseMainManifest` output). It fails if either permission is present, if `allowBackup` is not `false`, or if any resolved `releaseRuntimeClasspath` artifact matches the E1.7 denylist. The failure message names the offending item.
  4. The checker logic is a pure function in `buildSrc`, unit-tested with fixture manifests.
  5. `NetworkBlockedTest` (instrumented) opens a TCP socket to `1.1.1.1:443` and expects a `SecurityException` or an `IOException` whose message contains `EACCES` or `EPERM` or "Permission denied".
- **Validation:**
  - `./gradlew :buildSrc:test` (or the `buildSrc` test task) passes with these fixture cases:
    - Given a manifest with INTERNET, expect failure naming INTERNET.
    - Given one with ACCESS_NETWORK_STATE, expect failure.
    - Given `allowBackup="true"`, expect failure.
    - Given a clean manifest, expect pass.
    - Given a classpath containing `com.google.firebase:firebase-analytics`, expect failure.
  - `./gradlew :app:verifyPrivacy` passes on the real project.
  - `./gradlew :app:ciGroupDebugAndroidTest --tests "*NetworkBlockedTest*"` passes on all three devices.

#### T0.4 License review (agent drafts, HUMAN approves, H2)
- **Depends on:** T0.2. **PRD:** 3.1, 4.2, 8, 11.5.
- **Touches:** `docs/license-review.md`, `NOTICE`.
- **Requirements:**
  1. For each asset, record the license name, URL, whether commercial use is allowed, whether redistribution in an app and in a public GitHub Release is allowed, and the attribution text:
     - MobileCLIP-S0 weights and code.
     - The face embedding model.
     - GeoNames `cities15000`.
     - ML Kit (Google APIs Terms).
     - Every LiteRT and AndroidX dependency, grouped by license.
  2. **Face embedding model:** list at least three candidates, with license and output dimension for each. Prefer the first that allows commercial redistribution. Models from InsightFace are excluded (non-commercial license).
  3. **Fallbacks:**
     - If MobileCLIP-S0 weights do not allow commercial redistribution, propose the best-licensed alternative of at most 70 MB INT8 total with 512-dimension output (for example an OpenCLIP model with a permissive weights license) and record its accuracy on a public benchmark.
     - If no face model qualifies, propose using CLIP image embeddings of face crops as the face vector, and flag that the 95% purity target may not be met.
  4. **Compatibility with our license:** for every asset and dependency, confirm its license allows use in a closed-source, commercial app under PolyForm Strict, and note any attribution or notice it requires. Copyleft licenses (GPL, LGPL with static linking, AGPL) are rejected. Record ML Kit's and Play Core's Google terms as "free, closed source, allowed".
  5. Every approved asset goes into `NOTICE`.
- **Validation:**
  - `docs/license-review.md` has a row for every asset with no empty cells, including a "Compatible with PolyForm Strict" column.
  - Human approval is recorded in the Decisions log (H2).
  - **Stop condition:** do not start T0.5 until H2 is recorded.

#### T0.5 Model conversion pipeline and model pack
- **Depends on:** T0.4. **PRD:** 3.1, 3.2, 5.2.
- **Touches:** `tools/model-conversion/` (scripts, `requirements.txt`, `README.md`), `models/models.lock.json`, `models_pack/build.gradle.kts`, `models_pack/src/main/assets/models/manifest.json`, `tools/model-conversion/golden/`.
- **Produces:**
  - Files `clip_image_int8.tflite`, `clip_text_int8.tflite`, `face_embed_int8.tflite`, `clip_vocab.txt`, `clip_merges.txt`.
  - `manifest.json`: one entry per model with `name`, `file`, `version` (string, starts at `"1"`), `sha256`, `inputWidth`, `inputHeight`, `inputLayout` (`NHWC` or `NCHW`), `mean` (3 floats), `std` (3 floats), `outputDim`, `license`.
  - Golden files `golden/tokens.json` (50 strings and their 77-token ids), `golden/image_embeddings.json` (10 reference images and their FP32 and INT8 vectors), `golden/text_embeddings.json` (20 prompts with FP32 and INT8 vectors), and the 10 reference images in `golden/images/`.
  - Gradle task `:models_pack:downloadModels`, run automatically before `:app` assembles.
- **Requirements:**
  1. Conversion uses LiteRT tooling (for example `ai-edge-torch`) from the official model checkpoints approved in T0.4. Use full INT8 weight quantization; activations stay float if full INT8 hurts accuracy beyond requirement 3.
  2. The image encoder input is the model's native size (256x256 for MobileCLIP-S0), and `mean` and `std` come from the model's reference preprocessing.
  3. INT8 versus FP32 on 100 images from a public permissively licensed dataset: mean cosine similarity at least 0.98 for image embeddings and for text embeddings.
  4. The total size of all files in the pack is at most 70 MB.
  5. Upload the files to the GitHub Release `models-v1` (agent via `gh release create`, or H3).
  6. `models.lock.json` lists URL and sha256 per file. `downloadModels` downloads into `models_pack/src/main/assets/models/`, verifies every sha256, fails on mismatch, and skips files that already match.
- **Validation:**
  - `python -I tools/model-conversion/verify.py` prints the mean cosine for image and text, both at least 0.98, and the total size at most 70 MB.
  - `./gradlew :models_pack:downloadModels` on a clean checkout downloads and verifies all files. Running it again is a no-op.
  - Corrupting one local file and rerunning makes the task re-download it.
  - `./gradlew :app:bundleRelease`, then `bundletool` reports a total download size under 100 MB.

#### T0.6 Model runtime spike and microbenchmarks
- **Depends on:** T0.5. **PRD:** 5.2, 8.
- **Touches:** `:ml` (minimal `ModelRegistry`, `ClipImageEncoder`, `ClipTextEncoder`), `:microbenchmark`, `:testing` (synthetic image generator for benchmarks).
- **Produces:** the minimal `ModelRegistry`, `ClipImageEncoder`, and `ClipTextEncoder` (E5.2). Raw versions: the text encoder takes pre-tokenized ids from the golden file until T2.1 (tokenizer); T2.3 finalizes both.
- **Requirements:**
  1. Load models from assets via `ModelRegistry` and the manifest. Interpreters run on the LiteRT CPU (XNNPACK) delegate with 4 threads by default. A GPU delegate variant is selectable for benchmarking.
  2. Microbenchmarks:
     - `clipImageEncodeOnly` (256x256 bitmap in memory).
     - `decodePreprocessEncode12MP` (synthetic 4000x3000 JPEG on disk, generated in setup, through decode, downsample, crop, and encode).
     - `clipTextEncode` (one prompt).
     - `vectorScan50k` and `vectorScan100k`: a brute-force dot product over random INT8 vectors, implemented inline for the spike. It is replaced by T2.4's `VectorIndex` once that exists.
  3. Each benchmark runs for CPU and GPU delegates where relevant, and reports the median, P90, and peak native heap.
- **Validation:**
  - `./gradlew :microbenchmark:connectedReleaseAndroidTest` on `api34` (emulator, sanity only) completes.
  - **DEVICE (H4):** the same command on the Realme. The human sends back the JSON. The agent records a results table in the Decisions log: medians, the derived photos per second from `decodePreprocessEncode12MP`, and the chosen delegate.

#### T0.7 Widget image limits spike
- **Depends on:** T0.2. **PRD:** 4.3.
- **Touches:** `:widget` (throwaway `SpikeWidget`, removed in T1.11), `:testing`.
- **Requirements:**
  1. Build a Glance widget that renders 5 photos (one visible, prev and next buttons cycle through them) at the maximum widget size (4x4 cells).
  2. Find the largest per-photo bitmap edge (in px, in steps of 64) such that 5 bitmaps render without `TransactionTooLargeException` or a "bitmap memory exceeds maximum" error, on `api29`, `api34`, `apiLatest`, and the Realme launcher.
- **Validation:**
  - An instrumented test on the three emulators renders the widget via the Glance testing library at the chosen edge size, with no exception.
  - **DEVICE (H4):** the human adds the widget on the Realme home screen and taps next 5 times with no blank frame.
  - Record in the Decisions log: `WIDGET_MAX_EDGE_PX = <value>` (expected around 512 to 768). T1.11 uses this value.

#### Gate G0
- [ ] T0.1 to T0.7 merged.
- [ ] Realme results recorded. Evaluate:
  - **Indexing throughput:** if below 5 photos per second, apply fallbacks in order, re-measuring after each:
    1. Use the GPU delegate.
    2. Decode with a larger `inSampleSize` (target at most 512 px on the long side before crop).
    3. Run Tier 3 only on photos where face detection finds a face larger than 80 px.
    4. Stop and ask the human to approve a revised target (E2.5).
  - **Scan:** `vectorScan50k` median must be under 50 ms. If not: use a contiguous `ByteArray` with manual loop unrolling, then split the scan across 4 coroutines.
  - **Text encode:** `clipTextEncode` median plus `vectorScan50k` median must be under 250 ms (leaves 50 ms for UI). If not, stop and report.
  - **Size:** the download size check from T0.5 passes.
- [ ] `WIDGET_MAX_EDGE_PX` recorded.
- [ ] `FEATURES.md` matches the plan (`python -I tools/check-features.py` passes), and every feature whose tasks are all done reads accurately.
- [ ] Commit the tracker: `Phase 0 complete: tracker update`.

---

### Phase 1: MVP (Local Gallery, On This Day, Basic Widget)

#### T1.1 Core domain types and date rules
- **Depends on:** T0.2. **PRD:** 4.2, 4.5, 12.1.
- **Touches:** `:core` packages `model`, `rules`.
- **Produces:** `GeoPoint`, `JunkReason`, `PhotoMeta`, `CaptureTime`, `ExclusionRule`, `Eligibility` (E5.1).
- **Requirements:**
  1. **`CaptureTime.effective`:**
     - A timestamp is valid if it is at or after `1990-01-01T00:00:00Z` (631152000000 ms) and at or before `nowUtcMillis + 86_400_000`.
     - Return `dateTakenUtcMillis` if valid, else `dateAddedUtcMillis` if valid, else `null`.
  2. **`Eligibility.forMemories`** returns `false` when any of these is true:
     - `isVideo`.
     - `isHidden`.
     - `junkReason != null && !notJunkOverride`.
     - The effective time is `null`.
     - A `Folder` rule's prefix matches `relativePath`. Match case-insensitively, after normalizing both to end with `/`.
     - A `DateRange` rule contains the effective time (inclusive).
     - A `Person` rule's `personId` is in `personIdsInPhoto`.

     Otherwise it returns `true`.
- **Validation:** `./gradlew :core:test --tests "*CaptureTime*" --tests "*Eligibility*"` passes with:
  - Given dateTaken 2020-05-01 and a valid dateAdded, expect dateTaken.
  - Given dateTaken `null`, expect dateAdded.
  - Given dateTaken = 0 (1970), expect dateAdded.
  - Given dateTaken = now + 2 days, expect dateAdded.
  - Given both invalid, expect `null`.
  - Given a hidden photo, expect `false`.
  - Given junk SCREENSHOT with override `true`, expect `true`.
  - Given junk with no override, expect `false`.
  - Given folder rule `DCIM/WhatsApp` and path `dcim/whatsapp/Images/`, expect `false`.
  - Given folder rule `DCIM/Whats` and path `DCIM/WhatsApp/`, expect `true` (the prefix matches on whole path segments only).
  - Given a date range that contains the time, expect `false`.
  - Given a person rule 7 and persons {3, 7}, expect `false`.
  - Given a video, expect `false`.
  - Given a clean photo, expect `true`.

#### T1.2 On This Day selection and widget picking
- **Depends on:** T1.1. **PRD:** 4.2, 4.3. **Review Focus:** 5.
- **Touches:** `:core` package `memories`.
- **Produces:** `OnThisDayGroup`, `OnThisDaySelector`, `MemoryPicker` (E5.1).
- **Requirements:**
  1. Input photos are already eligibility-filtered. Convert each effective time to a local date in `zone` (the device's current zone; the capture zone is not known, and this is a documented limitation).
  2. **Matching:** for a photo with local date D, check its anniversary (same month and day) in the years `today.year - 1`, `today.year`, and `today.year + 1`. If D is Feb 29 and the anniversary year is not a leap year, use Feb 28.
  3. A photo matches if some anniversary A satisfies `|A - today| <= windowDays` days, and `yearsAgo = A.year - D.year >= 1`. If several anniversaries match, use the one closest to today.
  4. **Grouping:** group by `yearsAgo`, ascending. Within a group, sort by capture time ascending. Drop empty groups.
  5. **`MemoryPicker.pick`:**
     - Take round-robin across groups in ascending `yearsAgo`.
     - Within each group, favorites come first. The rest are in a deterministic shuffle seeded by `seed.toEpochDays()`.
     - Stop at `maxItems`.
     - The same input and seed always gives the same output.
- **Validation:** `./gradlew :core:test --tests "*OnThisDay*" --tests "*MemoryPicker*"` passes with (zone `UTC` unless stated):
  - Given today 2026-03-10 and photos 2025-03-10, 2024-03-13, 2024-03-14, 2026-03-10, expect groups `[1: [2025-03-10]]` and `[2: [2024-03-13]]`.
  - Given a photo 2024-02-29 and today 2026-02-28, expect a match with yearsAgo 2. Today 2026-03-03: match. Today 2026-03-04: no match.
  - Given today 2026-01-02 and a photo 2020-12-30, expect a match with yearsAgo 5.
  - Given today 2026-01-02 and a photo 2025-12-31, expect no match (yearsAgo would be 0).
  - Given zone `Asia/Manila` and a photo at 2024-06-15T23:30Z, expect local date 2024-06-16 (matches today 2026-06-16).
  - Given zone `Europe/Berlin` and a photo at 2023-03-26T00:30 local (a daylight saving day), expect local date 2023-03-26.
  - Given empty input, expect an empty list.
  - Given 3 groups with 4 photos each, `maxItems` 5, and one favorite in group 2, expect 5 items: the first from group 1, the second is group 2's favorite, and so on round-robin.
  - Given the same seed twice, expect equal lists. Given seeds one day apart over 20 photos, expect different orders.

#### T1.3 Room database schema v1
- **Depends on:** T1.1. **PRD:** 4.7, 5.9.
- **Touches:** `:data` packages `db`, `db/entity`, `db/dao`; `data/schemas/`.
- **Produces:** `MemoriesDatabase` v1 with these tables (snake_case columns; every boolean is an INTEGER 0 or 1):
  - `photo`:
    - Identity and file: `media_id` (PK, the MediaStore `_ID`), `volume_name`, `relative_path` (nullable), `display_name`, `mime_type`, `is_video`, `duration_ms` (nullable), `width`, `height`, `size_bytes`.
    - Dates: `date_taken_utc` (nullable), `date_added_utc`, `date_modified_utc`.
    - Location: `latitude`, `longitude` (nullable), `gps_read`.
    - User and junk flags: `is_favorite_local`, `is_hidden`, `junk_reason` (nullable text), `not_junk_override`.
    - CLIP flags (set in T2.10): `is_chat_screenshot` (default 0), `is_pet` (default 0).
    - Rediscover: `rediscover_cycle` (default 0), the cycle in which the photo was last shown (T3.11).
    - Indexing state: `tier1_version` (default 0), `clip_model_version` (nullable), `face_version` (default 0), `index_error_count` (default 0).
    - Access: `is_accessible` (default 1).
    - Indices on `date_taken_utc`, `relative_path`, `junk_reason`, `is_accessible`.
  - `embedding`: `media_id` (PK, FK to `photo`, cascade delete), `vector` (BLOB, 512 bytes), `scale` (REAL), `model_version`.
  - `person`: `id` (PK auto), `name` (nullable), `cover_face_id` (nullable), `user_confirmed`, `created_utc`, `centroid` (nullable BLOB of float32).
  - `face`: `id` (PK auto), `media_id` (FK cascade), `left`, `top`, `right`, `bottom` (REAL, normalized 0 to 1), `vector` (BLOB), `scale`, `model_version`, `person_id` (nullable FK to `person`, set null on delete). Indices on `media_id`, `person_id`.
  - `cannot_link`: `face_id`, `person_id` (composite PK).
  - `trip`: `id` (PK auto), `start_utc`, `end_utc`, `title`.
  - `event`: `id` (PK auto), `start_utc`, `end_utc`, `median_lat`, `median_lon` (nullable), `place_title` (nullable), `trip_id` (nullable FK to `trip`, set null), `cover_media_id`.
  - `event_photo`: `event_id`, `media_id` (composite PK, both FK cascade).
  - `exclusion_rule`: `id` (PK auto), `type` (`FOLDER`, `DATE_RANGE`, `PERSON`), `folder_prefix`, `start_utc`, `end_utc`, `person_id` (all nullable).
  - `widget_config`:
    - `app_widget_id` (PK), `source_type` (`ON_THIS_DAY`, `PERSON`, `PETS`, `FAVORITES`, `FOLDER`, `USER_ALBUM`), `source_arg` (nullable), `refresh_period` (`HOURLY`, `SIX_HOURS`, `DAILY`).
    - Style: `corner_radius_dp` (default 16), `border` (`NONE`, `THIN`, `THICK`, default `NONE`), `dynamic_color` (default 1).
    - State: `current_index` (default 0), `last_refresh_utc`.
  - `vault_item`: `id` (PK auto), `encrypted_file` (unique), `thumb_file`, `original_display_name`, `original_relative_path`, `mime_type`, `date_taken_utc` (nullable), `size_bytes`, `created_utc`.
  - `album`: `id` (PK auto), `kind` (`USER`, `ROTATING`, `REDISCOVER`), `title` (nullable; `null` for `REDISCOVER`), `cover_media_id` (nullable, FK to `photo`, set null on delete), `created_utc`, `published_utc` (nullable), `candidate_key` (nullable, unique). Index on `kind`.
  - `album_photo`: `album_id` (FK cascade), `media_id` (FK to `photo`, cascade), `position` (INTEGER), `added_utc`. Composite PK (`album_id`, `media_id`). Index on `media_id`.
  - `rotation_history`: `candidate_key` (PK), `published_utc`. Never deleted by the app.
- **Requirements:**
  1. `exportSchema = true`, with JSON committed in `data/schemas/`. Any later schema change bumps the version and adds a Room migration plus a `MigrationTestHelper` test. Destructive migration is never allowed.
  2. `PhotoDao` provides:
    - Batch upsert.
    - Mark inaccessible by id list.
    - Delete by id list.
    - A paging source over accessible, non-hidden rows ordered by `COALESCE(date_taken_utc, date_added_utc) DESC, media_id DESC`.
    - A hidden-only paging source.
    - `pendingTier1(version, limit)`, `pendingClip(modelVersion, limit)`, `pendingFaces(version, limit)`: accessible, non-video rows below that version, at most `limit`.
    - `allForMemories()`: accessible, non-video, non-hidden rows.
  3. Album membership rows are removed only by the `photo` cascade (permanent delete, T1.4) or by the user. Album queries filter `is_accessible = 1 AND is_hidden = 0` at read time, so membership survives trash and partial access.
  4. The mapper from row to `PhotoMeta` lives in `:data` (`PhotoMapper`).
- **Validation:** `./gradlew :data:ciGroupDebugAndroidTest --tests "*Dao*"` passes with:
  - Given a photo with an embedding and a face, deleting the photo removes both.
  - Given 3 photos with dates (null taken, 2024, 2025), expect paging order 2025, 2024, then the null one by `date_added`.
  - Given a hidden photo, expect it absent from the main source and present in the hidden source.
  - Given an inaccessible photo, expect it absent from both sources and from `allForMemories`.
  - `pendingClip("1", 10)` returns only rows whose `clip_model_version` is null or not `"1"`, and excludes videos.
  - Given a photo in a user album, setting `is_accessible = 0` keeps its `album_photo` row and hides it from the album query; setting it back to 1 shows it again. Deleting the photo row removes the `album_photo` row.
  - Inserting a second `album` row with the same `candidate_key` fails.
  - `data/schemas/.../1.json` exists.

#### T1.4 MediaStore sync
- **Depends on:** T1.3, T1.5. **PRD:** 4.1, 5.5. **Review Focus:** 1, 2, 4.
- **Touches:** `:data` package `media` (`MediaStoreSource`, `MediaSyncer`, `PhotoRepository`); `:testing` (`TestPhotoSeeder`); `tools/test-photos/`.
- **Produces:** `MediaSyncer`, `PhotoRepository` (E5.2); `TestPhotoSeeder.seed(specs: List<TestPhotoSpec>): List<Long>`, where `TestPhotoSpec` holds width, height, mime, EXIF dateTimeOriginal (nullable), GPS (nullable), relative path, and display name. The seeder writes real JPEG or PNG files through MediaStore with the given EXIF.
- **Requirements:**
  1. **What to read:** query images and videos on every volume from `MediaStore.getExternalVolumeNames`.
     - Columns: `_ID`, `VOLUME_NAME`, `RELATIVE_PATH`, `DISPLAY_NAME`, `MIME_TYPE`, `WIDTH`, `HEIGHT`, `DATE_TAKEN` (ms), `DATE_ADDED` and `DATE_MODIFIED` (seconds; convert to ms), `SIZE`, `DURATION` (videos).
     - On API 30+ also read `IS_FAVORITE` and `GENERATION_MODIFIED`.
  2. **Change detection:** on API 30+, store the last seen generation per volume in DataStore and fetch only rows with `GENERATION_MODIFIED` greater than it. On API 29, compare `DATE_MODIFIED` and `SIZE` per id.
  3. **Deletions:** each sync queries `_ID` only for all rows, then diffs against the database.
     - When access is `FULL`, missing ids are deleted.
     - On API 30+, the id query includes trashed items (`QUERY_ARG_MATCH_TRASHED = MATCH_INCLUDE`) and reads `IS_TRASHED`. Trashed rows are marked `is_accessible = 0`, not deleted, so a photo restored from the trash keeps its albums and labels (PRD 4.7). When Android empties the trash, the id disappears and the row is deleted as above.
     - When access is `PARTIAL`, missing ids are marked `is_accessible = 0` and are not deleted, so labels and embeddings survive. Rows seen again are set back to `is_accessible = 1`.
     - When access is `DENIED`, sync does nothing and returns `(0, 0, 0)`.
  4. Writes happen in transactions of 500 rows. GPS is not read here (T2.6 reads it).
  5. `sync()` is safe to call concurrently. A second caller waits for the running sync (a single mutex) and then returns that sync's result.
  6. **Progress:** `MediaSyncer.progress: StateFlow<SyncProgress?>` (`SyncProgress(done: Int, total: Int)`) is set while a sync that adds more than 500 rows runs, updated after each 500-row transaction, and `null` otherwise. Because rows commit every 500, the grid fills while sync runs.
  7. `:app` calls `sync()` on every foreground start. A `ContentObserver` on the images and videos URIs triggers a debounced `sync()` (2 s) while the process is alive.
  8. `tools/test-photos/generate.py` produces N tiny JPEGs (64x64) with EXIF dates spread over 10 years, and `push.sh` pushes them to `/sdcard/Pictures/MemoriesScale/` and triggers a media scan. These are used for DEVICE scale runs.
- **Validation:**
  - `./gradlew :data:ciGroupDebugAndroidTest --tests "*MediaSync*"` passes with:
    - Given 20 seeded photos, expect `added = 20`, with fields matching the specs.
    - Then delete 3 via `ContentResolver`, sync, and expect `removed = 3`.
    - Then rewrite 1 file, sync, and expect `updated = 1`.
    - Given a photo with no EXIF date, expect a row with `date_taken_utc` null.
    - Given a 0-byte `.jpg` inserted via MediaStore, expect sync to complete with no exception.
    - On `api34` with only `READ_MEDIA_VISUAL_USER_SELECTED` granted and nothing selected, expect every row `is_accessible = 0` and none deleted. Then grant `READ_MEDIA_IMAGES`, sync, and expect all `is_accessible = 1`.
    - Given access `DENIED`, expect `(0, 0, 0)` and no row changes.
    - On `api34`: trash a seeded photo (`createTrashRequest`, approved with UiAutomator), sync, and expect its row kept with `is_accessible = 0`. Restore it (`createTrashRequest(..., false)`), sync, and expect `is_accessible = 1`.
    - Given 2,000 seeded photos, `progress` emits increasing `done` values with `total = 2000`, then `null`.
    - Two concurrent `sync()` calls produce one set of changes.
  - Emulator scale: given 5,000 seeded photos, initial sync finishes in under 60 s on `api34`.
  - **DEVICE (H4):** push 50,000 tiny photos, then measure initial sync time from a log line `SYNC_DONE ms=<n>`. It must be under 30,000 ms. Then 100,000 (Review Focus 1): under 60,000 ms with no OOM.

#### T1.5 Photo access state
- **Depends on:** T0.2. **PRD:** 5.5.
- **Touches:** `:data` package `access`; `app/src/main/AndroidManifest.xml`.
- **Produces:** `PhotoAccessState`, `PhotoAccessChecker` (E5.2).
- **Requirements:**
  1. **Manifest permissions:**
     - `READ_EXTERNAL_STORAGE` with `maxSdkVersion="32"`.
     - `READ_MEDIA_IMAGES`, `READ_MEDIA_VIDEO`, `READ_MEDIA_VISUAL_USER_SELECTED`.
     - `ACCESS_MEDIA_LOCATION`.
  2. **`current()`:**
     - API 29 to 32: `FULL` if `READ_EXTERNAL_STORAGE` is granted, else `DENIED`.
     - API 33: `FULL` if `READ_MEDIA_IMAGES` is granted, else `DENIED`.
     - API 34+: `FULL` if `READ_MEDIA_IMAGES` is granted, else `PARTIAL` if `READ_MEDIA_VISUAL_USER_SELECTED` is granted, else `DENIED`.
  3. `hasMediaLocation()` returns whether `ACCESS_MEDIA_LOCATION` is granted.
  4. State is re-read on every `onResume` of `MainActivity` and exposed as a `StateFlow` from a `PhotoAccessMonitor` in `:data`.
- **Validation:** `./gradlew :data:ciGroupDebugAndroidTest --tests "*PhotoAccess*"` passes, using `UiAutomation.grantRuntimePermission` and `revokeRuntimePermission` to cover every branch on each device (PARTIAL only on `api34` and `apiLatest`).

#### T1.6 Screenshot rule
- **Depends on:** T1.1, T1.4. **PRD:** 3.1 Tier 1, 5.6.
- **Touches:** `:core` `rules/ScreenshotRule.kt`; `:data` `MediaSyncer` (sets `junk_reason = SCREENSHOT` on insert when the rule matches and `tier1_version == 0`).
- **Produces:** `ScreenshotRule` (E5.1).
- **Requirements:** `isScreenshot` is `true` if any of these holds:
  - Any `relativePath` segment equals `Screenshots` (case-insensitive).
  - `displayName` starts with `Screenshot` (case-insensitive).
  - `mimeType == "image/png"` and (width, height) equals the screen size in either orientation, within plus or minus 1 px.

  The screen size comes from `WindowManager` maximum window metrics, passed in by `:data`.
- **Validation:**
  - `./gradlew :core:test --tests "*ScreenshotRule*"` passes with:
    - Given `DCIM/Screenshots/`, expect `true`.
    - Given `Pictures/screenshots/`, expect `true`.
    - Given display name `Screenshot_20260101.jpg`, expect `true`.
    - Given a PNG at 1080x2400 on a 1080x2400 screen, expect `true`.
    - Given a PNG at 2400x1080, expect `true`.
    - Given a JPEG at 1080x2400, expect `false`.
    - Given a PNG at 1079x2400, expect `true`.
    - Given a PNG at 1000x2400, expect `false`.
    - Given `DCIM/Camera/`, expect `false`.
  - An instrumented sync test seeds one photo in `Pictures/Screenshots/` and expects `junk_reason = SCREENSHOT`.

#### T1.7 App shell, onboarding, and permission UX
- **Depends on:** T1.5. **PRD:** 4.5, 5.5, 5.6, 10.3.
- **Touches:** `:app` (`MemoriesApp` with `@HiltAndroidApp`, `MainActivity`, `nav/`, `ui/theme/`, `ui/onboarding/`, `Features.kt`).
- **Produces:**
  - Navigation routes `onboarding`, `grid`, `viewer/{mediaId}`, `memories`, `albums`, `album/{albumId}`, `default-album/{name}`, `search`, `people`, `settings`, `hidden`, `licenses`, `vault`.
  - `Features` object with booleans `search`, `people`, `trips`, `widgetStudio`, `vault`. All are `false` in Phase 1; the owning tasks flip them.
- **Requirements:**
  1. Single activity, Compose, Material 3, edge-to-edge. Dynamic color on API 31+, with a fixed fallback palette below that.
  2. Bottom navigation: Photos, Memories, Albums (added by T1.15), and Search (only if `Features.search`), plus Settings in the top app bar.
  3. **Onboarding** (shown until completed, stored in DataStore):
     1. Welcome: the privacy statement "Your photos never leave this phone. Memories has no internet access."
     2. Photo access request.
     3. Optional location access, explaining that it is used to find trips. Skippable.
     4. Done.
  4. **Access states:**
     - `DENIED`: a full-screen explanation with a "Allow access" button. If the system won't show the dialog again, the button opens `ACTION_APPLICATION_DETAILS_SETTINGS`.
     - `PARTIAL`: a dismissible banner on the grid reading "Memories can only see the photos you selected." with a "Change" button that re-requests access.
  5. Configuration changes (rotation, dark mode) keep the onboarding step and screen state.
- **Validation:** `./gradlew :app:ciGroupDebugAndroidTest --tests "*Onboarding*" --tests "*AccessState*"` passes:
  - Fresh install shows Welcome.
  - Granting access reaches Done and then the grid.
  - Revoking access while in the background, then resuming, shows the DENIED screen.
  - On `api34`, PARTIAL shows the banner.
  - Rotating on step 2 stays on step 2.

#### T1.8 Photo grid
- **Depends on:** T1.4, T1.7. **PRD:** 4.1.
- **Touches:** `:app` `ui/grid/`; `:benchmark` (`StartupBenchmark`, `GridScrollBenchmark`); baseline profile generator.
- **Requirements:**
  1. Paging 3 over the `PhotoDao` main source, with month headers (for example "March 2026") using the local zone.
  2. **Layout:**
     - Adaptive columns with a minimum cell size of 96 dp.
     - Thumbnails come from Coil, requested at the cell's pixel size, from content URIs.
     - Videos show a play badge and their duration (`m:ss`).
  3. **States:**
     - Empty: "No photos yet".
     - Loading: placeholders, no spinners per cell.
     - First scan: while `MediaSyncer.progress` is not null, photos appear as they are found, with a thin progress bar under the top app bar and the line "Finding your photos: X of Y". Both disappear when it finishes.
  4. A baseline profile is generated and included for startup and grid scroll.
- **Validation:**
  - Compose UI test: given 100 seeded photos across 3 months, expect 3 headers in order, and a video shows the badge.
  - Compose UI test with a fake `progress` flow: the bar and "Finding your photos" line show while it is not null and hide after.
  - **DEVICE (H4)**, run `:benchmark` on the Realme with 5,000 seeded photos:
    - `StartupBenchmark` cold start time to initial display median under 1,000 ms.
    - `GridScrollBenchmark` `frameDurationCpuMs` P90 under 12 ms and P99 under 24 ms.

#### T1.9 Photo viewer and actions
- **Depends on:** T1.8. **PRD:** 4.1, 4.5 (Hide).
- **Touches:** `:app` `ui/viewer/`, `media/MediaActions.kt`.
- **Produces:** `MediaActions`, which returns a sealed `ActionRequest` (`LaunchIntentSender`, `LaunchIntent`, `Done`, `NeedsConfirm`) for share, favorite, trash or delete, edit, and hide, so the API-level branching is unit-testable.
- **Requirements:**
  1. **Viewer:** a horizontal pager over the grid order. Double tap zooms 2.5x; pinch zooms up to 5x. Images are decoded at screen size.
  2. **Actions:**
     - **Share:** `ACTION_SEND` chooser with read grant.
     - **Favorite:** on API 30+, `MediaStore.createFavoriteRequest`. On API 29, toggle `is_favorite_local`.
     - **Delete:**
       - On API 30+, `MediaStore.createTrashRequest`.
       - On API 29, first show a confirm dialog: "This permanently deletes the photo. Android 10 has no trash." Then `ContentResolver.delete`. On `RecoverableSecurityException`, launch its `userAction`.
     - **Edit:** an `ACTION_EDIT` chooser with read and write grants. If no app can handle it, show the toast "No photo editor installed."
     - **Hide or Unhide:** set `is_hidden`. Hidden photos move to the `hidden` album (Settings).
     - **Info:** a sheet with date, dimensions, size, path, and coordinates if known.
  3. Video items open with `ACTION_VIEW` (no in-app playback in v1).
  4. After trash or delete, trigger `sync()` and move the pager to the next item. Deleting the last item closes the viewer.
- **Validation:**
  - Unit tests for `MediaActions`, one per action, for API 29 and API 30+.
  - Instrumented on `api34`: trash a seeded photo, using UiAutomator to click the system confirm button matching `(?i)(allow|move|trash)`. Expect the photo gone from the grid after sync (its row is kept with `is_accessible = 0`, T1.4).
  - If the system dialog can't be automated on the ATD image, mark that case as a DEVICE check.
  - On `api29`: delete flow with the confirm dialog.

#### T1.10 Memories screen, MemoryService, and story viewer
- **Depends on:** T1.2, T1.8. **PRD:** 4.2, 4.3 (Tap-to-Story). **Review Focus:** 4.
- **Touches:** `:engine` `memory/MemoryService.kt`; `:app` `ui/memories/`, `story/StoryActivity.kt`.
- **Produces:** `MemoryService` (E5.2); the deep link contract in E5.2.
- **Requirements:**
  1. **`today()`:** loads `allForMemories()`, filters with `Eligibility` (using exclusion rules from the database; empty until T3.7), and runs `OnThisDaySelector` with the current zone. The result is cached until the local date changes or the `photo` table changes.
  2. **`forWidget(OnThisDay, max)`:**
     1. `MemoryPicker.pick(today(), max, todayDate)`.
     2. If that is empty, fall back to eligible favorites shuffled with the date seed.
     3. If still empty, fall back to eligible photos from the same calendar month in earlier years.
     4. Otherwise, return an empty list.
  3. **Memories screen:** one card per group. Titles use a plurals resource ("1 year ago", "N years ago"). Cover is the group's first picked photo. Tapping opens the story with `memory_type=on_this_day` and `memory_key="otd:<yyyy-MM-dd>:<yearsAgo>"`.
  4. **`StoryActivity`:**
     - **Display:** full-screen and immersive. Shows 5 s per photo, with a progress bar per photo at the top.
     - **Gestures:** tap the right third for next and the left third for previous. Long press pauses. Swipe down closes. Closes after the last photo.
     - **Launching:** runs in its own task (`taskAffinity` separate, `excludeFromRecents="true"`) so opening it from the widget does not bring up the gallery.
  5. **Missing photos:** a photo that fails to open is skipped silently. If none can open, close with the toast "This memory is no longer available."
  6. **Empty Memories screen:** when there are no cards, show "No memories from this day yet. Your photos from today will be here next year." with an "Open camera" button (`MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA`; hidden if no app handles it).
- **Validation:**
  - Instrumented tests:
    - Given seeded photos on today's date 1 and 3 years ago, expect 2 cards in order.
    - Open a story, delete its second photo from outside, then advance: expect the third photo shown and no crash.
    - Given all photos deleted, expect the toast and the activity finished.
    - Given no photos on today's date in past years, the Memories screen shows the empty message and the "Open camera" button.
  - Unit test of the `forWidget` fallback chain with a fake repository.
  - **DEVICE (H4):** `StoryStartupBenchmark` cold launch via the deep link, with time to first photo displayed under 1,000 ms on the Realme.

#### T1.11 Daily Memory widget (basic)
- **Depends on:** T0.7, T1.10. **PRD:** 4.3. **Review Focus:** 4.
- **Touches:** `:widget` (`DailyMemoryWidget`, `DailyMemoryWidgetReceiver`, `WidgetRefreshWorker`, `WidgetImageLoader`, `NavActions`). Remove `SpikeWidget`.
- **Produces:** `WidgetSource.OnThisDay`, `RefreshPeriod` (E5.1).
- **Requirements:**
  1. **Widget info:** minimum 2x2 cells, resizable up to 4x4, with a preview image. Description: "Shows a memory from this day in past years."
  2. **Layout:**
     - A photo with crop-fill and corner radius 16 dp.
     - A bottom scrim with the label (for example "3 years ago").
     - Previous and next buttons. Their `ActionCallback`s update `widget_config.current_index` modulo the item count and refresh the widget.
  3. **Data:**
     - Items come from `MemoryService.forWidget(OnThisDay, 5)`.
     - Bitmaps are decoded at `min(WIDGET_MAX_EDGE_PX, widget size in px)`.
     - At most 5 items per widget.
  4. **Tap:** opens `StoryActivity` with `memory_type=widget` and `memory_key="widget:<appWidgetId>:<yyyy-MM-dd>"`, starting at the current item.
  5. **Refresh:**
     - Unique periodic work per widget using its `refresh_period` (default `DAILY`).
     - A one-time work scheduled for the next local midnight plus 5 minutes.
     - A refresh after any sync that changed rows.
  6. **Lifecycle:** a `widget_config` row is created on first update (`ON_THIS_DAY`, `DAILY`) and deleted in `onDeleted`, along with its work.
  7. **Empty and error states:**
     - No items: "No memories today" plus the app icon. Tapping opens the app.
     - An item URI that fails to open is skipped. If all fail, show the empty state.
     - Access `DENIED`: "Open Memories to allow photo access."
- **Validation:**
  - Glance unit tests (`glance-appwidget-testing`):
    - The label renders.
    - The empty state renders.
    - Next pressed 5 times cycles back to the first item.
    - The DENIED state renders.
  - Instrumented: add, then delete, a widget id through `AppWidgetManager` test helpers. Expect the config row created, then removed, with its work cancelled.
  - **DEVICE (H4):**
    - Add the widget on the Realme with seeded "On This Day" photos.
    - Tap next through all items.
    - Tap the photo and expect the story to open without the gallery.
    - Delete the shown photo in another app and refresh: expect the next photo or the empty state.

#### T1.12 Settings, hidden album, licenses
- **Depends on:** T1.9. **PRD:** 4.5, 11.5.
- **Touches:** `:app` `ui/settings/`, `ui/hidden/`; AboutLibraries plugin (offline, generated at build time).
- **Requirements:**
  1. **Settings** has these entries:
     - Privacy: static text explaining no internet access, no backup, and that the code is published for inspection, with the repo URL as plain text.
     - Hidden photos.
     - Licenses.
     - About (version name and code).
     - Send feedback: `ACTION_SENDTO` with a `mailto:` address from `strings.xml` (`mhonn.ybanez@gmail.com`). The body is prefilled with the app version and Android version only. This is the opt-in feedback channel from 5.8.
  2. **Hidden album:** a grid of hidden photos, each with "Unhide".
  3. **Licenses screen:** AboutLibraries list plus the `NOTICE` assets. It must work offline.
- **Validation:** Compose UI tests:
  - Hide a photo in the viewer: it appears in Hidden and not in the grid. Unhide it: it is back in the grid.
  - The Licenses screen lists at least one AndroidX entry and "GeoNames" (once added in T3.1; until then, the `NOTICE` entries present).

#### T1.13 Memory reminders
- **Depends on:** T1.10, T1.12. **PRD:** 4.6, 5.5, 10.3.
- **Touches:** `:core` `reminder/ReminderPlanner.kt`, `reminder/ReminderCopy.kt`, `memories/MonthAgoSelector.kt`; `:data` `settings/ReminderSettings.kt`; `:engine` `reminder/` (`ReminderScheduler`, `DailyReminderPlanWorker`, `ReminderWorker`, `ReminderNotifier`, `DateCandidateSource`), `MemoryService` (adds `monthAgo()`); `:app` `ui/settings/` (two switches), `story/StoryActivity.kt` (records the opened memory date, adds the `month_ago` story), `res/values/strings.xml` (every line in PRD 4.6), manifest (`POST_NOTIFICATIONS`).
- **Produces:** `ReminderKind`, `ReminderFamily`, `ReminderLine`, `ReminderCandidate`, `ReminderCopy`, `MonthAgoSelector`, `ReminderPlanner` (E5.1); `ReminderSettings`, `ReminderCandidateSource`, `ReminderScheduler` (E5.2).
- **Requirements:**
  1. **Planner (`:core`, pure):** `slotsFor` returns one slot per kind, at random times inside `[windowStart, windowEnd)` local time, at least `minGapMinutes` apart, in random order. Times that fall in a daylight saving gap move forward to the next valid minute. The same `Random` seed gives the same result.
  2. **Daily plan:** `DailyReminderPlanWorker` is unique periodic work (24 h) that also runs as one-time work at the next local midnight plus 10 minutes. It calls `slotsFor` for today and enqueues one unique one-time `ReminderWorker` per enabled kind with the matching delay, replacing any earlier plan for the day. Slots already in the past are skipped. No exact alarms and no `SCHEDULE_EXACT_ALARM`.
  3. **Rescheduling:** `ReminderScheduler.ensureScheduled()` runs at app start. `rescheduleToday()` runs when a switch changes or the time zone changes (`ACTION_TIMEZONE_CHANGED`). WorkManager keeps the work across reboots.
  4. **Line bank (`ReminderCopy.LINES`):** every line in PRD 4.6, each with a stable id (for example `years_ago_1`, `nudge_highlight`), its string resource names, allowed time window, and `sensitive = true` for place and get-together lines. All lines are defined now so strings are translated together; a line is only used when a candidate for its family exists.
  5. **Choosing a line (`ReminderCopy.choose`, pure):**
     - `MEMORY`: take the candidates' highest-precedence family (E5.1 order; within the theme families, highest `score`). Pick at random among that family's lines not in `history` within the last 7 days. If all were used, pick the least recently used. Return `null` when there are no candidates.
     - `CAPTURE`: filter nudge lines by `now` against `allowedFrom` and `allowedUntil` (`nudge_highlight`: from 17:00; `nudge_up_to`: until 15:00), then apply the same 7-day rule.
  6. **Date candidates (`DateCandidateSource`):**
     - `YEARS_AGO`: from `MemoryService.today()`; `yearsAgo` is the smallest group's value (the most recent memory), cover from `MemoryPicker.pick(today(), 1, today)`, `memory_type=on_this_day`.
     - `MONTH_AGO`: from `MemoryService.monthAgo()` (eligible photos via `MonthAgoSelector`, at least 3 photos), `memory_type=month_ago`, `memory_key="month:<yyyy-MM-dd>"`. The story shows up to 15 photos in time order.
  7. **Memory nudge (`ReminderWorker`, `MEMORY`):** at fire time, skip if it is later than `windowEnd` plus 30 minutes, or the local date is no longer the plan's date (Doze can delay work for hours), if the switch is off or `lastMemoryOpenedDate` is today. Collect candidates from every `ReminderCandidateSource`, call `choose`, and skip if it returns `null`. Post the line's title and body with the values filled in (plurals for N), and `BigPictureStyle` with the cover decoded at most 512 px on the long edge. If the cover fails to open, post without a picture. Tapping opens `StoryActivity` with the candidate's `memory_type` and `memory_key`. Record the line id in `lineHistory`.
  8. **Capture nudge (`ReminderWorker`, `CAPTURE`):** apply the same late-skip rule, then call `choose` and post the nudge line. Tapping fires `MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA`. If no app handles it, open the app instead. No camera permission is needed. Record the line id in `lineHistory`.
  9. **Channels:** `reminder_memory` ("Memory reminders") and `reminder_capture` ("Photo reminders"), default importance. Notifications auto-cancel on tap.
  10. **Lock screen:** `VISIBILITY_PRIVATE` with a public version that never has a picture. Its title is the line's title, or "A memory is waiting" when the line is `sensitive`.
  11. **Opened memory:** when `StoryActivity` opens any memory except `widget` from any entry point, it sets `lastMemoryOpenedDate` to today.
  12. **Permission (Android 13+):** request `POST_NOTIFICATIONS` the first time the Memories screen shows at least one card, at most once (stored in DataStore), and again when the user turns on a switch while it is denied. If denied, the switches stay visible with the note "Notifications are off for Memories" and a button to the system notification settings. Never request it during onboarding.
  13. **Settings:** a "Reminders" section in Settings (T1.12) with "Memory reminders" and "Photo reminders" switches, both on by default.
  14. **Privacy:** no log line contains a file path, place name, person name, or notification text. Notification text may contain place names (T3.9) but never file paths or person names.
  15. **Speed budget:** each `ReminderWorker` run finishes in under 1,000 ms with 50,000 photos, cold process included. It logs `REMINDER_DONE ms=<n>` (no other content). T2.9 and T3.9 must keep within this budget.
- **Validation:**
  - Unit tests (`:core`) for `slotsFor`, over 10,000 seeds:
    - Every slot is inside the window, and the two slots are at least 180 min apart.
    - Both orders happen.
    - The spring-forward and fall-back days in `America/New_York` give valid times.
    - The same seed gives the same slots.
  - Unit tests (`:core`) for `ReminderCopy.choose`:
    - Given `YEARS_AGO` and `MONTH_AGO` candidates, a years-ago line is chosen.
    - A line used 6 days ago is not chosen while another is available; one used 8 days ago can be.
    - With every fitting line used in the last 7 days, the least recently used is chosen.
    - At 10:00 the highlight nudge is never chosen; at 18:00 the "What are you up to today?" nudge is never chosen.
    - No candidates for `MEMORY` returns `null`.
    - Every line in `LINES` has string resources that exist (checked against `strings.xml` in an `:app` unit test).
  - Unit tests for `MonthAgoSelector`: 30 days back across a month end, Mar 30 back to Feb 28 or 29, and plus or minus 1 day.
  - Instrumented tests (WorkManager `work-testing`):
    - With both switches on, the daily plan enqueues two reminder works. With one off, it enqueues one. With both off, none.
    - Memory nudge with no candidates: no notification posted. With an On This Day memory: one notification on `reminder_memory` with a years-ago title. With only photos from 30 days ago: a "This time last month" title, and tapping opens the month-ago story. With `lastMemoryOpenedDate` set to today: none.
    - After a nudge posts, its line id is in `lineHistory`.
    - A reminder work for today run at 21:31 local time, or run after midnight for yesterday's plan, posts nothing.
    - Capture nudge posts on `reminder_capture` with no memory present.
    - With the notification permission denied, the workers finish without crashing and post nothing.
  - Compose UI test: turning a switch off cancels that kind's pending work.
  - **DEVICE (H4):**
    - On the Realme with seeded On This Day photos, use a debug-only setting (present only when `BuildConfig.DEBUG`) that sets the window to the next 10 minutes and the gap to 2 minutes, and confirm both notifications arrive.
    - Tap each one: the story opens for the memory nudge, and the camera opens for the capture nudge.
    - With the phone locked, the memory nudge shows no photo.
    - With 50,000 seeded photos (T1.4 tools), force-stop the app, then trigger each reminder work with `adb shell cmd jobscheduler run`: `REMINDER_DONE ms` is under 1,000 for both kinds.

#### T1.14 Multi-select
- **Depends on:** T1.9, T1.12. **PRD:** 4.1, 4.7 (Multi-select).
- **Touches:** `:app` `ui/selection/` (`SelectionState`, `SelectionTopBar`, `SelectionBottomBar`), `media/SelectionActions.kt`, `ui/grid/`, `ui/hidden/`.
- **Produces:** `SelectionActions` share, hide, and trash (E5.2); a reusable `SelectionState` that T1.15 (albums) and T2.7 (search results) plug into.
- **Requirements:**
  1. **Entering and leaving:** long-press a photo to enter selection mode with it selected. Tapping toggles a photo. The top bar shows "N selected" and a close button. Back or close exits. The selection survives rotation and is cleared after an action completes.
  2. **Select all:** in the Hidden album (and, from T1.15, every album). It selects every photo in that source, not only loaded pages, using an id query on the DAO. The main grid has no Select all.
  3. **Actions:**
     - **Share:** `ACTION_SEND_MULTIPLE` chooser with read grants. Over `SHARE_LIMIT` (100), show "You can share up to 100 photos at a time." and share nothing.
     - **Hide or Unhide** (Unhide in the Hidden album): one database transaction for all ids.
     - **Trash, API 30+:** one `MediaStore.createTrashRequest` for all URIs (one system dialog).
     - **Trash, API 29:** if more than `API29_TRASH_LIMIT` (20) are selected, first show "Android 10 can delete up to 20 photos at a time. Delete the first 20?" (Delete, Cancel), keeping the selection order. Then the T1.9 confirm dialog once, then `ContentResolver.delete` per photo. A `RecoverableSecurityException` launches that photo's `userAction`, one at a time, in order.
     - After Trash or Hide, call `sync()` (Trash only) and exit selection mode.
  4. Videos can be selected, shared, hidden, and trashed like photos.
- **Validation:**
  - Unit tests for `SelectionActions.trash` and `share`:
    - API 30 with 50 ids: one `LaunchIntentSender` carrying 50 URIs.
    - API 29 with 25 ids: `NeedsConfirm` limited to the first 20 ids. With 5 ids: `NeedsConfirm` with 5.
    - Share with 101 ids returns the limit message; with 100, one chooser intent with 100 URIs.
  - Compose UI tests: long-press enters selection, a tap adds a photo, "2 selected" shows, back exits. In the Hidden album with 300 seeded hidden photos, Select all then Unhide puts all 300 back in the grid.
  - Instrumented on `api34`: select 3 photos, Trash, approve the single system dialog with UiAutomator: all 3 are gone from the grid after sync. On `api29`: select 3, Delete, confirm: all 3 are deleted.

#### T1.15 Albums tab, user albums, and default albums
- **Depends on:** T1.3, T1.6, T1.14. **PRD:** 4.7. **Review Focus:** 4.
- **Touches:**
  - `:core` package `albums` (`AlbumKind`, `DefaultAlbum`, `JunkAlbumRule`).
  - `:data` package `albums` (`AlbumDao`, `AlbumRepository`, `AlbumSettings`).
  - `:app` `ui/albums/`, the album picker sheet, the viewer overflow ("Add to album"), the selection bar (album actions), `ui/settings/` ("Restore default albums"), `nav/` (Albums tab).
- **Produces:** `AlbumKind`, `DefaultAlbum`, `JunkAlbumRule` (E5.1); `AlbumRepository` (user and default), `AlbumSettings` (`removedDefaults`), `SelectionActions` album actions (E5.2).
- **Requirements:**
  1. **Albums tab:** in the bottom navigation after Memories. Three sections, in order, each hidden when empty:
     1. "Your albums": `USER` albums, newest created first, after a "New album" tile.
     2. "Collections": default albums with at least 1 photo that are not in `removedDefaults`, in `DefaultAlbum` order.
     3. "Suggested for you": `ROTATING` albums (filled by T3.10), newest published first.

     Each tile shows the cover, title, and photo count.
  2. **Album contents rule** (every album kind): rows with `is_accessible = 1 AND is_hidden = 0`, ordered by `COALESCE(date_taken_utc, date_added_utc) DESC`, then `position`. Videos are allowed and show the play badge. Junk and exclusion rules do not filter user albums (PRD 4.7).
  3. **Cover:** `cover_media_id` if it passes the contents rule, else the newest photo that does, else a placeholder.
  4. **New album:** a dialog asks for a title (trimmed, 1 to 60 characters; duplicates allowed). The empty album opens with an "Add photos" button that opens the grid in selection mode with an "Add" action.
  5. **Add to album:** from the viewer overflow and the selection bar. A bottom sheet lists user albums (newest first) with "New album" at the top. Photos already in the album are skipped; new ones get `position = max + 1` in selection order.
  6. **Inside a user album:** overflow actions Rename, Choose cover (single pick, plus "Use newest photo", which sets `cover_media_id = null`), and Delete album, with the confirm text "Delete album \"{title}\"? Photos stay in your gallery." Selection mode here has Select all, Remove from album, Add to album, Share, Hide, and Trash.
  7. **`JunkAlbumRule.albumFor`:** `null` if `isHidden`, `notJunkOverride`, or `junkReason == null`. Otherwise `SCREENSHOT` gives `CHAT_SCREENSHOTS` when `isChatScreenshot`, else `SCREENSHOTS`; `RECEIPT` and `DOCUMENT` give `RECEIPTS_DOCUMENTS`; `BLURRY` gives `BLURRY`. The `AlbumDao` default-album queries implement the same rule in SQL.
  8. **Default albums in Phase 1:** `FAVORITES` (photos where `PhotoMeta.isFavorite` is true, using the same columns as `PhotoMapper`) and `SCREENSHOTS` (by T1.6 path and size rule). The others appear once T2.10 and T3.11 fill them. Exclusion rules do not apply to Favorites or junk albums. Default albums open with Select all, Add to album, Share, Hide, and Trash.
  9. **Removing a default album:** long-press its tile, "Remove album", confirm "Remove \"{title}\" from Albums? Photos stay in your gallery. You can restore it in Settings." This adds it to `removedDefaults`. Settings shows "Restore default albums" while the set is not empty; it clears the set.
  10. **Speed:** `defaultCounts()` and `observeAlbums()` each emit first in under 300 ms with 50,000 photo rows and 50 user albums.
- **Validation:**
  - `./gradlew :core:test --tests "*JunkAlbumRule*"` passes: SCREENSHOT with chat `false` gives `SCREENSHOTS`, with chat `true` gives `CHAT_SCREENSHOTS`; RECEIPT and DOCUMENT give `RECEIPTS_DOCUMENTS`; BLURRY gives `BLURRY`; `notJunkOverride` gives `null`; hidden gives `null`; no reason gives `null`.
  - `./gradlew :data:ciGroupDebugAndroidTest --tests "*Album*"` passes:
    - Create an album, add 3 photos, add one of them again: count 3. Remove 1: count 2, and that photo is still in the grid. Delete the album: all photos still in the grid.
    - Hide a photo in an album: absent from the album, present in Hidden. Unhide: back in the album.
    - Review Focus 4, on `api34`: trash a photo that is in an album, sync: absent from the album. Restore it, sync: back in the album with the same position.
    - Hiding the cover photo makes the newest remaining photo the cover.
    - Remove `SCREENSHOTS`: absent from `observeAlbums` sections; restore: present.
    - The DAO default queries agree with `JunkAlbumRule` over 200 generated rows covering every combination.
    - With 50,000 inserted photo rows and 50 albums, the first emission of `defaultCounts()` and `observeAlbums()` is under 300 ms on `api34`.
  - Compose UI tests: section order is Your albums, Collections, Suggested for you; Suggested is absent with no rotating albums; Favorites appears only after a photo is favorited; New album with a blank title keeps the Create button disabled.

#### T1.16 Background reliability check
- **Depends on:** T1.12, T1.13. **PRD:** 5.3.
- **Touches:** `:engine` `health/BackgroundHealth.kt`; `:data` `ReminderSettings` (adds `lastPlanRunDate`); `:app` `ui/health/` (card and help screen), `ui/memories/`, `ui/settings/`, `res/values/strings.xml`; `buildSrc` privacy checker.
- **Produces:** `BackgroundHealth`, `HealthIssue` (E5.2).
- **Requirements:**
  1. **`BackgroundHealth.issues(): Flow<Set<HealthIssue>>`**, re-evaluated on every foreground start:
     - `RESTRICTED`: `ActivityManager.isBackgroundRestricted()` is true, or `UsageStatsManager.getAppStandbyBucket()` is `STANDBY_BUCKET_RESTRICTED` (API 31+).
     - `RARE`: the standby bucket is `STANDBY_BUCKET_RARE`.
     - `HIBERNATION`: `PackageManagerCompat.getUnusedAppRestrictionsStatus()` reports the restriction is enabled (androidx.core).
     - `MISSED_REMINDERS`: at least one reminder switch is on and `lastPlanRunDate` is 2 or more days before today. `DailyReminderPlanWorker` (T1.13) sets `lastPlanRunDate` each time it runs.
     - `STALLED_INDEXING` is added in T2.6.
  2. **Card:** "Allow Memories to run in the background so reminders and photo analysis keep working." The subtitle names the issue (for example "Reminders haven't run for 2 days" or "Android pauses apps you haven't used in a while"). Buttons:
     - "Fix in settings": for `HIBERNATION`, `IntentCompat.createManageUnusedAppRestrictionsIntent`; otherwise `ACTION_APPLICATION_DETAILS_SETTINGS`.
     - "Show me how": opens the help screen.
     - Shown at the top of Memories while any issue exists, dismissible for 7 days. Always shown in Settings under Reminders while any issue exists. `RARE` alone shows only in Settings.
  3. **Help screen:** built-in steps chosen by `Build.MANUFACTURER`: Realme and Oppo, Xiaomi (including Redmi and POCO), Samsung, and a generic Android fallback. Each entry covers background activity, battery saver exceptions, and "Pause app activity if unused". All text is in string resources. Nothing is loaded from the web. Entries other than Realme carry a code comment "verify on device" until checked.
  4. **No exemption request:** the app never declares or requests `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`. `verifyPrivacy` (T0.3) fails if the release merged manifest contains it.
  5. **Privacy:** issues are never logged with anything beyond the issue name.
- **Validation:**
  - Unit tests of the issue rules with fake system values: restricted gives `RESTRICTED`; rare bucket gives `RARE`; `lastPlanRunDate` 2 days old with a switch on gives `MISSED_REMINDERS`; with both switches off, none.
  - `buildSrc` fixture test: a manifest with `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` fails `verifyPrivacy`.
  - Compose UI tests: the card shows on Memories with `MISSED_REMINDERS`, hides after dismiss, and returns after 7 days; `RARE` alone shows only in Settings; the help screen shows the Realme steps when the manufacturer is "realme" and the generic steps for an unknown maker.
  - Instrumented on `api34`: `adb shell am set-standby-bucket <package> restricted` makes `RESTRICTED` appear on next start.
  - **DEVICE (H4), on the Realme:**
    1. With default battery settings, enable both reminders, swipe the app away from recents, and leave the phone overnight. Next day: record whether both reminders arrived and `lastPlanRunDate` advanced.
    2. Repeat with Realme's background activity for Memories turned off. Record whether reminders arrive, and whether the app shows `MISSED_REMINDERS` 2 days later.
    3. Follow the in-app Realme steps exactly and confirm they match the phone's settings screens; correct the text if not.
    4. Results go in the Decisions log.

#### Gate G1
- [ ] T1.1 to T1.16 merged. `./gradlew ciGroupDebugAndroidTest` passes for all modules.
- [ ] DEVICE checks from T1.4, T1.8, T1.10, T1.11, T1.13, and T1.16 pass on the Realme (H4).
- [ ] `:app:verifyPrivacy` passes on the release build.
- [ ] Tag `v0.1.0` on `main`. Start R1 (human).
- [ ] `FEATURES.md` matches the plan (`python -I tools/check-features.py` passes), and every feature whose tasks are all done reads accurately.
- [ ] Commit the tracker: `Phase 1 complete: tracker update`.

---

### Phase 2: On-Device AI & Search

#### T2.1 CLIP tokenizer
- **Depends on:** T0.5. **PRD:** 3.1 Tier 2.
- **Touches:** `:core` package `search/ClipTokenizer.kt`.
- **Produces:** `ClipTokenizer` (E5.1).
- **Requirements:**
  1. Behavior must match the reference tokenizer used for the approved model (OpenCLIP-style CLIP BPE):
     - Cleanup: HTML unescape, whitespace collapse, lowercase.
     - The reference split regex.
     - The byte-to-unicode mapping and BPE merges from `clip_merges.txt`.
  2. Output format: start token 49406, end token 49407, and context length 77. Text longer than that is truncated so the end token sits at index 76. Pad with 0.
  3. Merge results are cached per word (bounded LRU of 10,000 entries).
- **Validation:** `./gradlew :core:test --tests "*ClipTokenizer*"` passes:
  - All 50 cases in `golden/tokens.json` match exactly. These include empty string, emoji, accented letters, punctuation runs, numbers, and a 300-word string.
  - Encoding 1,000 short queries takes under 1 s on the JVM test runner.

#### T2.2 Image preprocessing
- **Depends on:** T0.6. **PRD:** 3.1.
- **Touches:** `:ml` `image/ImagePreprocessor.kt`.
- **Produces:** `ImagePreprocessor` (E5.2).
- **Requirements:**
  1. **Decoding:** use `ImageDecoder` with a software allocator (`ALLOCATOR_SOFTWARE`) and ARGB_8888. Orientation is applied by the decoder. HEIC and HEIF must decode.
  2. **`decodeForAnalysis(uri, maxSide)`:** returns a bitmap whose long side is at most `maxSide`. The aspect ratio is preserved.
  3. **`decodeForClip(uri)`:** scale so the short side equals the manifest input size, then center-crop to `inputWidth` x `inputHeight`.
  4. A `fromBitmap(bitmap)` variant derives the CLIP crop from an already-decoded analysis bitmap, so the pipeline decodes each photo once.
  5. **Tensor conversion:** `(pixel/255 - mean)/std` per channel, using the manifest's `inputLayout`.
  6. **Errors:** a corrupt, empty, or unsupported file returns `null`, never throws, and logs `w` without the path.
- **Validation:** `./gradlew :ml:ciGroupDebugAndroidTest --tests "*ImagePreprocessor*"` passes:
  - Given a seeded 400x200 JPEG with EXIF orientation 6, expect the decoded bitmap to be portrait (height greater than width).
  - Given a 4000x3000 JPEG, `decodeForAnalysis(1024)` returns 1024x768, and `decodeForClip` returns 256x256.
  - Given a 0-byte file and a truncated JPEG, expect `null`.
  - Given a HEIC sample, expect it to decode.

#### T2.3 Final CLIP encoders and model registry
- **Depends on:** T2.1, T2.2, G0. **PRD:** 3.1, 3.2.
- **Touches:** `:ml` `ModelRegistry`, `ClipImageEncoder`, `ClipTextEncoder`, `di/MlModule.kt`.
- **Produces:** final `ModelRegistry`, `ClipImageEncoder`, `ClipTextEncoder` (E5.2), and `ModelRegistry.clipVersion: String`.
- **Requirements:**
  1. **Delegate:** use the one chosen at G0. If GPU delegate creation fails at runtime, fall back to CPU and log `w` once.
  2. **Output:** both encoders return L2-normalized vectors of length 512.
  3. **Threading:** each encoder instance owns one interpreter and is not thread-safe (document this in KDoc). Provide `close()`. Hilt provides them as factories, not singletons, for the pipeline. The search path uses one shared text encoder guarded by a `Mutex`.
  4. **Loading:** models load lazily on first use. A missing model file throws `ModelMissingException` with the model name. It cannot happen in release builds because the pack is install-time, but tests cover it.
- **Validation:** `./gradlew :ml:ciGroupDebugAndroidTest --tests "*ClipEncoder*"` passes:
  - For the 10 golden images, through `decodeForClip` then `embed`, cosine with the golden INT8 vector is at least 0.97 each.
  - For the 20 golden prompts, cosine with the golden INT8 text vector is at least 0.995 each.
  - Zero-shot sanity: for golden `dog.jpg`, the prompt "a photo of a dog" scores highest among the 20 golden prompts.

#### T2.4 Vector quantization and index
- **Depends on:** T1.1. **PRD:** 3.1 Tier 2, 5.2. **Review Focus:** 1.
- **Touches:** `:core` package `search` (`VectorMath`, `VectorQuantizer`, `VectorIndex`); `:microbenchmark` (replace the spike scan).
- **Produces:** `EMBEDDING_DIM`, `QuantizedVector`, `VectorQuantizer`, `VectorMath`, `ScoredMedia`, `VectorIndex` (E5.1).
- **Requirements:**
  1. **Quantization:**
     - Input must be L2-normalized (`|norm - 1| < 1e-3`, else `IllegalArgumentException`).
     - `scale = maxAbs / 127`, or 0 if `maxAbs` is 0.
     - `q_i = round(v_i / scale)` clamped to the range -127 to 127.
  2. **Score:** `scale * sum(q_i * query_i)`, with `query` a normalized float vector.
  3. **Storage:**
     - One contiguous `ByteArray` (slots x 512), a `FloatArray` of scales, a `LongArray` of ids, and a map from id to slot. Capacity grows by 1.5x.
     - `put` on an existing id overwrites it.
     - `remove` swaps the last slot into the removed slot.
  4. **Search:**
     - Scores every allowed slot and keeps the top `topK` with a bounded min-heap.
     - Results are sorted by score descending. Ties are broken by `mediaId` descending.
     - Given `topK <= 0` or an empty index, returns an empty list.
  5. The class is not thread-safe (KDoc). Callers serialize access.
- **Validation:**
  - `./gradlew :core:test --tests "*Vector*"` passes:
    - Given 10,000 random normalized vectors and 100 random queries, the INT8 top-10 overlaps the exact float top-10 by at least 9 of 10 on average.
    - `put` twice with the same id leaves `size` unchanged and uses the second vector.
    - `remove` of a middle id, then search, never returns it, and every other id is still found.
    - An `allow` filter excluding the best match returns the next best.
    - A non-normalized input throws.
  - **DEVICE (H4):** microbenchmarks `vectorScan50k` median under 50 ms and `vectorScan100k` median under 100 ms on the Realme.

#### T2.5 Tier 1 junk filter
- **Depends on:** T2.3, T2.4. **PRD:** 3.1 Tier 1, 5.7.
- **Touches:**
  - `:core` `rules/JunkClassifier.kt`, `rules/LaplacianVariance.kt`, `resources/junk_thresholds.json`, `resources/junk_prompts.json`.
  - `:ml` `TextDetector`.
- **Produces:** `JunkSignals`, `JunkThresholds`, `JunkClassifier`, `LaplacianVariance` (E5.1); `TextDetector` (E5.2); `TIER1_VERSION = 1` constant in `:core`.
- **Requirements:**
  1. **`LaplacianVariance`:** take a grayscale image (long side 512, from `decodeForAnalysis(512)`), apply the 3x3 kernel `[0,1,0; 1,-4,1; 0,1,0]` over interior pixels, and return the variance of the response.
  2. **Prompts** (`junk_prompts.json`):
     - Document: "a photo of a document", "a scanned page of text".
     - Receipt: "a photo of a receipt", "a shopping receipt".
     - Negatives: "a photo of people", "a photo of a landscape", "a photo of food", "a photo of an animal", "a photo of a building", "a photo of an object".
     - A class score is the sum of softmax probabilities (`softmax(100 * cosine)` over all 10 prompts) of that class's prompts.
     - Prompt embeddings are computed once per model version and cached in memory.
  3. **OCR gate:** OCR (`TextDetector`, ML Kit bundled Latin) runs only if `max(documentScore, receiptScore) >= ocrGateMin`.
     - `charCount` is the total number of non-whitespace characters.
     - `textAreaRatio` is the sum of text block box areas divided by the image area, capped at 1.0.
  4. **Decision**, in order. The first rule that matches wins:
     1. `isScreenshot` gives `SCREENSHOT`.
     2. `receiptScore >= receiptMin && (ocrCharCount ?: 0) >= receiptCharMin` gives `RECEIPT`.
     3. `documentScore >= documentMin && (ocrCharCount ?: 0) >= documentCharMin && (ocrTextAreaRatio ?: 0f) >= documentAreaMin` gives `DOCUMENT`.
     4. `laplacianVariance < blurMax` gives `BLURRY`.
     5. Otherwise, `null`.
  5. **Initial `junk_thresholds.json`:** `ocrGateMin 0.30`, `receiptMin 0.70`, `receiptCharMin 40`, `documentMin 0.70`, `documentCharMin 80`, `documentAreaMin 0.15`, `blurMax 25.0`. T2.8 tunes these values. The keys never change.
- **Validation:**
  - `./gradlew :core:test --tests "*Junk*" --tests "*Laplacian*"` passes:
    - A uniform image gives 0.0.
    - A 1-px checkerboard scores greater than the same checkerboard box-blurred with radius 3.
    - Precedence: a screenshot with high receipt score gives `SCREENSHOT`.
    - A receipt score above threshold but with 10 characters gives not `RECEIPT`.
    - Every rule boundary is exact (at threshold matches, just below does not).
    - Missing OCR (`null`) never yields `RECEIPT` or `DOCUMENT`.
  - Instrumented: golden `receipt.jpg` gives `RECEIPT`, golden `landscape.jpg` gives `null`.

#### T2.6 Indexing pipeline, scheduling, thermal guard, and Index now
- **Depends on:** T1.4, T1.6, T2.5. **PRD:** 3.2, 5.3, 5.6. **Review Focus:** 2, 3.
- **Touches:**
  - `:core` `index/IndexPriority.kt`.
  - `:engine` package `index` (`IndexPipeline`, `IndexWorker`, `NewMediaWorker`, `IndexScheduler`, `IndexStatus`, `ThermalGuard`, `IndexNowService`).
  - `:data` (pending queries exclude `index_error_count >= 3`).
  - `app/src/main/AndroidManifest.xml`.
- **Produces:** `IndexPriority`, `IndexScheduler`, `IndexStatus`, `ThermalGuard` (E5); `IndexPipeline.run(budgetMillis: Long, isStopped: () -> Boolean): RunResult(processed: Int, remaining: Int, pausedThermal: Boolean)`; a hook interface `IndexStage` (Tier 3 plugs in during T3.4).
- **Requirements:**
  1. **`IndexPriority.order`** puts photos in these buckets, in order:
     1. Photos that would appear in On This Day for any date from today to today plus 7 days.
     2. Photos with an effective time in the last 90 days.
     3. Everything else.

     Within each bucket, newest first. A `null` effective time goes last.
  2. **Per photo**, in one database transaction at the end:
     1. Decode once with `decodeForAnalysis(1024)` (or the G0 fallback size).
     2. Read GPS with `ExifInterface` on `MediaStore.setRequireOriginal(uri)` when `hasMediaLocation()` and `gps_read == 0`, then set `gps_read = 1`.
     3. Compute Tier 1 signals and run `JunkClassifier`.
     4. Compute the CLIP embedding from `fromBitmap`.
     5. Write the embedding, the junk reason, the tier versions, and the GPS.
  3. **Failures:** a decode returning `null` or any exception increments `index_error_count`. The pipeline moves on and never aborts the batch.
  4. **Batching:** 200 photos per batch. Before each batch:
     - If `isStopped()`, return.
     - If `ThermalGuard.shouldPause()`, return with `pausedThermal = true`. This is true when `PowerManager.currentThermalStatus >= THERMAL_STATUS_MODERATE` or the battery is low.
     - If the elapsed time is over `budgetMillis`, return.
  5. **`IndexWorker`:**
     - Unique one-time work `index-pipeline` (`KEEP`), with constraints `requiresCharging`, `requiresDeviceIdle`, and `requiresStorageNotLow`. No backoff criteria (idle jobs do not allow them).
     - Calls `sync()`, then `IndexPipeline.run(budgetMillis = 9 min)`.
     - If work remains, enqueue unique work `index-continue` (`KEEP`) with the same constraints.
     - If paused for heat, enqueue `index-continue` with a 15-minute initial delay.
  6. **Scheduling:**
     - `IndexScheduler.ensureScheduled()`, called at app start, sets up unique periodic work `index-periodic` every 12 h with the same constraints, which enqueues `index-pipeline`.
     - `NewMediaWorker` has a content URI trigger on `MediaStore.Images.Media.EXTERNAL_CONTENT_URI` (descendants `true`). It runs `sync()`, enqueues `index-pipeline`, and re-enqueues itself.
  7. **`IndexStatus`:**
     - `indexed` counts accessible, non-video rows at the current Tier 1 and CLIP versions. `total` counts accessible, non-video rows.
     - `state` reflects running, paused for heat, waiting for the charger, or idle.
  8. **Index now (`IndexNowService`):**
     - A foreground service with `foregroundServiceType` `dataSync` on API 29 to 34 and `mediaProcessing` on API 35+.
     - Manifest permissions: `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_DATA_SYNC`, `FOREGROUND_SERVICE_MEDIA_PROCESSING`, `POST_NOTIFICATIONS` (requested on API 33+ before starting; the service still runs if denied).
     - Notification channel `indexing`, showing "Indexing photos: X of Y" and a "Stop" action.
     - Runs `IndexPipeline.run(budgetMillis = 6 h)` without the charging or idle requirement. The thermal guard still applies; on a heat pause, the notification reads "Paused to keep your phone cool" and the run resumes when the status drops below `MODERATE` (checked every 60 s).
  9. **Version upgrades:** the pending queries use the current `TIER1_VERSION` and `ModelRegistry.clipVersion`, so bumping either re-indexes automatically (3.2).
  10. **Stalled indexing:** `BackgroundHealth` (T1.16) reports `STALLED_INDEXING` when `indexed < total` and no batch has committed for 3 days. The card subtitle is "Photo analysis hasn't run lately", with an extra "Index now" button.
- **Validation:**
  - `./gradlew :core:test --tests "*IndexPriority*"` passes:
    - Given today 2026-03-10, a photo 2024-03-15 goes in bucket 1 (it is On This Day on 03-12 to 03-17), a photo 2026-02-01 goes in bucket 2, and a photo 2020-07-01 goes in bucket 3.
    - A `null` date goes last.
  - `./gradlew :engine:ciGroupDebugAndroidTest --tests "*IndexPipeline*" --tests "*IndexWorker*"` passes (WorkManager `TestDriver` satisfies the constraints):
    - Given 30 seeded photos including a corrupt file, after a run, 29 have embeddings and the corrupt one has `index_error_count = 1`.
    - Three runs on the corrupt file leave it excluded from pending.
    - A fake `ThermalGuard` returning `true` after the first batch (budget 1 batch, 450 photos) gives `pausedThermal = true` and `index-continue` enqueued with a 15-minute delay.
    - Kill test: start a run over 400 photos, call `force-stop` via `UiAutomation.executeShellCommand` midway, then run again to completion. Expect exactly 400 embedding rows, every row at the current versions, and no orphan rows.
    - Bumping `TIER1_VERSION` in a test override makes all rows pending again.
  - Unit test: with `indexed < total` and the last batch 3 days ago, `STALLED_INDEXING` is reported; after a new batch commits, it clears.
  - **DEVICE (H4):**
    - An `IndexPipeline` throughput microbenchmark over 500 synthetic 12 MP JPEGs reports at least 5 photos per second on the Realme.
    - Overnight run with 5,000 synthetic 12 MP photos and default Realme battery settings: by morning `IndexStatus.indexed == 5000`. Otherwise record the count and investigate before G2.

#### T2.7 Search, categories, and index status UI
- **Depends on:** T2.6, T1.13, T1.14. **PRD:** 4.4, 4.6, 4.7 (Multi-select), 5.6.
- **Touches:**
  - `:engine` `search/EmbeddingIndexHolder.kt`, `search/SearchService.kt`, `search/CategoryService.kt`.
  - `:core` `resources/category_prompts.json`.
  - `:app` `ui/search/`, `ui/progress/ProgressCard.kt` (shown on Memories and Search), the onboarding Done step, and the Settings index section.
  - `:engine` `reminder/AllSetNotifier.kt`.
  - `Features.search = true`.
- **Produces:** `EmbeddingIndexHolder`, `SearchService`, `CategoryService`, `Category` (E5.2).
- **Requirements:**
  1. **`EmbeddingIndexHolder`:**
     - Loads all embeddings with the current model version in pages of 1,000, on a background dispatcher at app start.
     - `IndexPipeline` calls `onEmbeddingsWritten(ids)` after each commit.
     - `:data` cannot depend on `:engine` (E4), so `MediaSyncer` gains `removedIds: SharedFlow<List<Long>>` (deleted or newly inaccessible ids). The holder collects it and removes those ids.
     - All access goes through one `Mutex`.
  2. **`SearchService.search`:**
     - The query is trimmed. A blank query returns an empty list.
     - Encode the query, then search with an `allow` filter of accessible and not hidden. Junk photos are included, because people search for receipts.
     - `limit` defaults to 200.
  3. **Categories:**
     - `PETS` and `FOOD`: zero-shot over prompts in `category_prompts.json`.
       - PETS: "a photo of a dog", "a photo of a cat", "a photo of a pet".
       - FOOD: "a photo of food", "a photo of a meal".
       - Each is scored against the same negatives as T2.5, minus food for FOOD and minus animal for PETS.
       - A photo is included when the class probability is at least 0.6.
     - `DOCUMENTS`: `junk_reason` in (RECEIPT, DOCUMENT).
     - `SCREENSHOTS`: `junk_reason = SCREENSHOT`.
     - `TRIPS`: hidden unless `Features.trips` (T3.3).
  4. **UI:**
     - A search field with a 300 ms debounce, and results in the grid cell style. Results support selection mode (`SelectionState`, T1.14), without Select all.
     - Category chips show when the query is empty.
     - The line "Indexed X of Y photos" shows while `X < Y`.
     - Empty results: "No matches. Search understands English."
  5. **Settings:** an "Index now" button and the live `IndexStatus`. The onboarding Done step says: "Search gets smarter overnight while your phone charges. You can also start it now in Settings."
  6. **"Getting to know your photos" card:** at the top of Memories and Search while `IndexStatus.indexed < total`.
     - Shows "Getting to know your photos: X of Y", a progress bar, and an "Index now" button (hidden while `RUNNING` from Index now). When `WAITING_FOR_CHARGER`, the subtitle is "Continues tonight while your phone charges." When `PAUSED_THERMAL`, "Paused while your phone cools down."
     - Lists what is ready and what is coming, one line per feature from a `ReadinessItem` list that later tasks add to. This task adds "Search: recent photos ready" once the last 90 days are indexed (from `IndexPriority`), then "Search: all photos ready".
     - Dismissible for the day; it comes back the next day if still not finished. It disappears for good when indexing first completes.
  7. **"All set" notification:** the first time indexing completes, post once on `reminder_memory`: title "Your photos are ready", text "Try searching \"beach at sunset\"." Tapping opens Search. Post only between 09:00 and 21:00 local time (otherwise wait for the next 09:00) and only if the memory reminder switch and the notification permission allow it. It replaces that day's memory nudge, so the two-a-day limit holds. Stored in DataStore so it never repeats.
- **Validation:**
  - Instrumented: seed the 10 golden images plus 40 fillers, run `IndexPipeline` directly, then:
    - "a dog" puts `dog.jpg` in the top 5.
    - Hiding `dog.jpg` removes it from results.
    - Deleting it via `ContentResolver`, then syncing, removes it from the holder.
    - The PETS chip includes `dog.jpg` and `cat.jpg`.
  - Compose UI test: blank query shows the chips; the "Indexed X of Y" line shows when they differ.
  - Compose UI test with a fake `IndexStatus`: the card shows on Memories and Search while indexing, shows the charger subtitle when `WAITING_FOR_CHARGER`, and is gone after completion.
  - Instrumented: completing indexing at 14:00 posts "Your photos are ready" once; completing again posts nothing; completing at 22:00 posts at 09:00 the next day; that day's memory nudge is skipped.
  - **DEVICE (H4):**
    - Holder load of 50,000 rows (debug seeding command `adb shell am broadcast -a dev.build_with_mhonn.memories.DEBUG_SEED_EMBEDDINGS --ei count 50000`, debug builds only) under 1,500 ms.
    - `searchService50k` microbenchmark (encode, scan, filter) median under 250 ms.

#### T2.8 Labeled quality set, evaluation, and threshold tuning
- **Depends on:** T2.7, T2.10. **PRD:** 4.7, 5.7, 10.1. **Human:** H5.
- **Touches:** `tools/labeled-set/` (`README.md`, `labels.json`, `ATTRIBUTION.md`, `fetch.sh`, `push.sh`, `tune.py`, `requirements.txt`); `:engine` androidTest `QualityEvalTest`.
- **Requirements:**
  1. **The set:** 2,000 photos from CC0 or CC BY sources (each attributed in `ATTRIBUTION.md`), plus consented staged photos. Target mix:
     - About 200 receipts or documents.
     - About 100 screenshots that are not chats, plus about 100 chat screenshots. Chat screenshots are generated by `tools/labeled-set/make_chats.py` from invented conversations in several chat-app styles (light and dark); no real messages.
     - About 100 blurry photos.
     - About 200 hard negatives: night shots, bokeh, menus, signs, whiteboards. Whiteboards count as `DOCUMENT`.
     - About 300 face photos covering 20 identities, from public-domain sources or consented people only.
     - The rest are everyday photos.
  2. **Storage:** images are stored in a GitHub Release `labeled-set-v1` as a zip, with its sha256 in `tools/labeled-set/lock.json`. Only `labels.json` is in git.
  3. **`labels.json` schema:**
     - `files`: a map from file name to `{ junk: null | "SCREENSHOT" | "RECEIPT" | "DOCUMENT" | "BLURRY", chat: boolean, identities: [string] }`. `chat` is `true` only for chat screenshots.
     - `queries`: a list of `{ text, relevant: [file names] }`, with 50 entries, each having at least 1 relevant file.
  4. **`QualityEvalTest`:**
     - Runs only when the instrumentation argument `runQualityEval=true` is set. This is opt-in, not a skip.
     - Indexes only `Pictures/LabeledSet/`, then writes `quality-report.json` and `signals.csv` (one row per photo with every `JunkSignals` field and the label).
     - Asserts the 5.7 targets for junk and search. T3.4 adds face purity.
     - Junk precision and recall are computed over all four reasons combined. A photo counts as correct if the predicted reason is non-null and the label is non-null.
     - Chat precision: of the photos with `is_chat_screenshot = 1`, the share labeled `chat: true`. Asserted at least 0.90 (PRD 5.7).
  5. **`tune.py`:**
     - Grid-searches `JunkThresholds` on a fixed 70% split (seed 42) to maximize recall subject to precision of at least 0.96.
     - Also tunes `chatMin` to maximize chat recall subject to chat precision of at least 0.92.
     - Reports the 30% holdout metrics and writes `core/src/main/resources/junk_thresholds.json`.
- **Validation:**
  - H5 recorded in the Decisions log.
  - `tools/labeled-set/push.sh` then `./gradlew :engine:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.runQualityEval=true --tests "*QualityEvalTest*"` on `api34` passes:
    - Junk precision at least 0.95.
    - Junk recall at least 0.80.
    - Search top-5 at least 0.80.
    - Chat precision at least 0.90.
  - `tune.py` holdout precision is at least 0.95 for junk and at least 0.90 for chats.
  - The resulting thresholds are recorded in the Decisions log.
  - **DEVICE (H4):** the same eval on the Realme meets the same targets.

#### T2.9 Theme reminder lines
- **Depends on:** T1.13, T2.7, T2.8. **PRD:** 4.6.
- **Touches:** `:engine` `reminder/ThemeCandidateSource.kt`, `CategoryService`; `:core` `resources/category_prompts.json`.
- **Requirements:**
  1. **Prompts:** add internal (not shown as chips) theme classes to `category_prompts.json`, scored the same way as PETS and FOOD in T2.7:
     - NATURE: "a photo of nature", "a photo of a forest", "a photo of mountains", "a photo of a beach", "a photo of a lake".
     - GROUP: "a group photo of friends", "a group of people at a party", "a family gathering".
  2. **`ThemeCandidateSource`:** for today's On This Day groups, compute the share of eligible, indexed photos in each theme (NATURE, GROUP, FOOD, PETS). Score only those photos: read their embeddings from the `embedding` table by id and compare them with the theme prompt embeddings. Those are written once per model version to `files/theme_prompts_<modelVersion>.bin` whenever the text encoder is already loaded (indexing or search). If the file is missing, the source returns no candidates for that run. Never load `EmbeddingIndexHolder` or the CLIP encoders for a reminder. A theme fits when at least 50% of the group's indexed photos and at least 3 photos match. `score` is that share. Map GROUP to `GET_TOGETHER`, NATURE to `NATURE`, FOOD to `FOOD`, PETS to `PETS`. The memory, cover, and `yearsAgo` are the group's, and the cover is the highest scoring photo for the theme.
  3. Groups with fewer than 3 indexed photos produce no theme candidate, so the years-ago line is used instead.
  4. Register it in the `ReminderCandidateSource` set. No changes to `ReminderCopy` are needed.
  5. Tune the 50% share and the NATURE and GROUP probability thresholds on the labeled set from T2.8 (add at least 50 nature and 50 group photos to it). Target: at least 90% of theme notifications on the labeled set name the right theme.
- **Validation:**
  - Instrumented: running the memory `ReminderWorker` in a fresh process leaves `EmbeddingIndexHolder` unloaded and loads no LiteRT model.
  - **DEVICE (H4):** the T1.13 reminder budget (under 1,000 ms with 50,000 photos) still passes with theme candidates present.
  - Unit test with a fake `CategoryService`: a group of 6 photos with 4 nature photos gives a `NATURE` candidate with score about 0.67; with 2 nature photos, none.
  - Instrumented: seed 5 beach photos dated 2 years ago today, index them, run the memory `ReminderWorker`, and expect the title "A moment with nature" and a second line naming 2 years.
  - Evaluation script reports theme precision on the labeled set; the result is recorded in E9.

#### T2.10 Chat screenshots, pet flag, and junk albums
- **Depends on:** T2.6, T2.7, T1.15. **PRD:** 4.7 (Default albums), 5.7.
- **Touches:**
  - `:core` `resources/category_prompts.json` (adds `CHAT`), `resources/junk_thresholds.json` (adds `chatMin`), `JunkThresholds`.
  - `:engine` `IndexPipeline` (writes `is_chat_screenshot` and `is_pet`).
  - `:data` `AlbumDao` (the remaining junk album queries).
  - `tools/test-photos/` (golden `chat.png` and `settings_screenshot.png`, generated, no real messages).
- **Requirements:**
  1. **Chat class:** prompts "a screenshot of a text message conversation", "a screenshot of a chat app". Negatives: "a screenshot of a web page", "a screenshot of a social media feed", "a screenshot of a game", "a screenshot of a map", "a screenshot of a settings screen", "a photo". The class score is the sum of `softmax(100 * cosine)` over these 8 prompts for the 2 chat prompts. `is_chat_screenshot = 1` when the photo's junk reason is `SCREENSHOT` and the score is at least `chatMin` (initial 0.60; T2.8 tunes it).
  2. **Pet flag:** `is_pet = 1` when the T2.7 PETS class probability is at least 0.6, for every indexed photo. T3.10 and T3.11 read this column so they never load models.
  3. Both flags are computed from the embedding the pipeline already has, in the same per-photo transaction (T2.6 requirement 2), with prompt embeddings cached per model version (as in T2.5). No extra decode.
  4. T2.10 merges before the first build that indexes testers' libraries (`v0.2.0`), so `TIER1_VERSION` stays 1.
  5. **Albums:** Chat screenshots, Receipts & documents, and Blurry now fill through `JunkAlbumRule`; Screenshots now leaves out chats.
- **Validation:**
  - `./gradlew :core:test --tests "*Junk*"` still passes, plus: a `chatMin` boundary test (at threshold is chat, just below is not), and a non-screenshot with a high chat score is never chat.
  - Instrumented (`api34`): seed `chat.png` and `settings_screenshot.png` in `Pictures/Screenshots/`, `receipt.jpg`, a blurred landscape, and `dog.jpg`, then run `IndexPipeline`:
    - `chat.png` is in Chat screenshots and not in Screenshots. `settings_screenshot.png` is in Screenshots.
    - `receipt.jpg` is in Receipts & documents; marking it "Not junk" removes it.
    - The blurred landscape is in Blurry.
    - `dog.jpg` has `is_pet = 1`; `receipt.jpg` has `is_pet = 0`.
  - **DEVICE (H4):** the T2.6 throughput microbenchmark still reports at least 5 photos per second on the Realme.

#### Gate G2
- [ ] T2.1 to T2.10 merged. All instrumented suites pass.
- [ ] DEVICE: throughput of at least 5 photos per second, `vectorScan50k` under 50 ms, `searchService50k` under 250 ms, the overnight 5,000-photo run complete, and quality targets (including chat precision) met on the Realme.
- [ ] `bundletool` download size under 100 MB.
- [ ] Tag `v0.2.0`. Upload to internal testing (R2 done), then start the closed test (R4, H7). The 14-day clock runs during Phases 3 and 4.
- [ ] `FEATURES.md` matches the plan (`python -I tools/check-features.py` passes), and every feature whose tasks are all done reads accurately.
- [ ] Commit the tracker: `Phase 2 complete: tracker update`.

---

### Phase 3: Smarter Memories & People

#### T3.1 Offline place lookup (GeoNames)
- **Depends on:** T1.1, T0.5. **PRD:** 4.2.
- **Touches:**
  - `tools/geonames/` (`build.py`, `README.md`, `requirements.txt`).
  - `models/models.lock.json` (adds `cities.bin`, fetched by `downloadModels` into `:models_pack` assets under `geo/`).
  - `:core` `geo/CityIndex.kt`, `geo/Haversine.kt`.
  - `NOTICE`.
- **Produces:** `City`, `CityIndex` (E5.1), plus `CityIndex.load(bytes: ByteArray)` and `CityIndex.countryName(code: String): String`.
- **Requirements:**
  1. `build.py` reads GeoNames `cities15000.txt`, `admin1CodesASCII.txt`, and `countryInfo.txt`. Snapshot them in the GitHub Release `geodata-v1` with sha256 recorded, so builds are reproducible.
  2. It writes `cities.bin`: a documented binary format (in `tools/geonames/README.md`) containing a country name table, then city records grouped by 1x1 degree cell. The size must be at most 3 MB.
  3. **`nearest`:**
     - Search the query's cell and expand ring by ring until the ring's minimum possible distance exceeds the best found distance or `maxKm`.
     - Longitude wraps at plus or minus 180.
     - Distance is haversine (Earth radius 6371.0088 km).
     - Ties go to the larger population.
     - Returns `null` if nothing is within `maxKm`.
  4. Add "GeoNames (CC BY 4.0), https://www.geonames.org" to `NOTICE` and the Licenses screen.
- **Validation:**
  - `python -I tools/geonames/build.py --verify` reports the record count (about 26,000 to 33,000) and a file size of at most 3 MB.
  - `./gradlew :core:test --tests "*CityIndex*" --tests "*Haversine*"` passes:
    - Real file: (38.7223, -9.1393) gives Lisbon, PT. (14.5995, 120.9842) gives Manila, PH. (0.0, -160.0) gives `null`.
    - Synthetic index: a city at lon 179.95 is found from a query at lon -179.95 (about 11 km).
    - Two cities at equal distance: the larger population wins.
    - Haversine from Lisbon to Madrid is 503 km plus or minus 5.
    - 10,000 random lookups take under 2 s on the JVM runner.

#### T3.2 Event grouping
- **Depends on:** T1.1. **PRD:** 4.2.
- **Touches:** `:core` `memories/EventGrouper.kt`.
- **Produces:** `EventCluster`, `EventGrouper` (E5.1); constant `MIN_EVENT_PHOTOS_FOR_MEMORY = 8`.
- **Requirements:**
  1. **Input:** eligible photos with a non-null effective time. Sort by effective time, then `mediaId`.
  2. **Splitting:** start a new event whenever the gap to the previous photo is greater than `gapMillis` (3 h).
  3. **`medianLocation`:** the coordinate-wise median of geotagged photos in the event, or `null` if none are geotagged.
- **Validation:** `./gradlew :core:test --tests "*EventGrouper*"` passes:
  - Photos at 10:00, 11:00, 15:01 give 2 events.
  - Photos at 10:00, 13:00 (exactly 3 h) give 1 event.
  - One photo gives 1 event.
  - Empty input gives an empty list.
  - Median of 3 geotagged plus 2 untagged uses only the 3.
  - 50,000 photos group in under 1 s on the JVM runner.

#### T3.3 Home area, trips, and event persistence
- **Depends on:** T3.1, T3.2, T2.6. **PRD:** 4.2.
- **Touches:**
  - `:core` `memories/HomeAreaEstimator.kt`, `memories/TripDetector.kt`.
  - `:engine` `memory/EventRebuildWorker.kt`, `MemoryService` (adds trips).
  - `:data` (event and trip DAOs).
  - `Features.trips = true`.
- **Produces:** `HomeAreaEstimator`, `Trip`, `TripDetector` (E5.1); `TripTitle` sealed interface (`City(name: String)`, `Country(code: String)`, `Countries(codes: List<String>)`). `Trip.title` is a `TripTitle`, and `:app` formats it with string resources. `MemoryService.trips(): List<TripMemory>`.
- **Requirements:**
  1. **`HomeAreaEstimator`:**
     - Use geotagged photos from the last 365 days, or all of them if there are fewer than 50.
     - Bucket them into cells of 0.045 degrees latitude by `0.045 / cos(lat)` degrees longitude (about 5 km).
     - Home is the mean point of the densest cell, or `null` if there are no points.
  2. **`TripDetector`:**
     1. A candidate is an event with a `medianLocation` more than 100 km from home.
     2. Merge consecutive candidates (in time order) when the gap is under 36 h and no event with a location within 100 km of home sits between them. Events without a location do not break a trip.
     3. Keep only merged groups that span at least 2 distinct local dates in `zone`.
     4. If `home` is `null`, there are no trips.
  3. **Title rules** (each geotagged event's median goes through `CityIndex.nearest`):
     - If all known cities are in one country and one city is the most frequent, the title is `City(name)`.
     - If one country but no city is resolved, `Country(code)`.
     - If more than one country, `Countries` sorted by photo count descending (at most 3).
  4. **`EventRebuildWorker`** (unique, charging and idle):
     - Runs after each indexing run that read new GPS data, and nightly.
     - Rebuilds `event`, `event_photo`, and `trip` in one transaction.
     - 50,000 photos must take under 60 s on the Realme.
  5. **`MemoryService.trips()`:** trips whose start date anniversary is within plus or minus 3 days of today (yearsAgo at least 1), plus trips that ended 1 to 30 days ago.
- **Validation:**
  - `./gradlew :core:test --tests "*HomeArea*" --tests "*TripDetector*"` passes:
    - Given 200 points in Manila and 20 in Lisbon, home is in Manila.
    - Given Lisbon events on Jun 1 and Jun 2 with home in Manila, expect 1 trip with title `City("Lisbon")`.
    - Given a Lisbon event and a Madrid event 20 h apart, expect 1 trip with `Countries(["PT","ES"])` or `["ES","PT"]` by count.
    - Given trip events with a home event between them, expect 2 trips.
    - A same-day-only far event gives no trip.
    - `home = null` gives no trips.
    - Untagged events between two trip events do not split them.
  - Instrumented: seeded geotagged photos, then pipeline plus rebuild, give the `trip` rows expected.
  - **DEVICE (H4):** rebuild time for 50,000 photos under 60 s.

#### T3.4 Faces: detection, embedding, clustering
- **Depends on:** T2.6, T2.8, T0.4. **PRD:** 3.1 Tier 3, 5.7.
- **Touches:**
  - `:ml` `FaceDetectorWrapper`, `FaceEmbedder`, `FaceAligner`.
  - `:core` `faces/FaceClusterer.kt`, `resources/face_thresholds.json`.
  - `:engine` (`FaceStage` as an `IndexStage`, `FaceReclusterWorker`).
  - `QualityEvalTest` (adds purity).
  - `tools/labeled-set/tune.py` (adds face thresholds).
- **Produces:** `FaceDetectorWrapper`, `FaceEmbedder` (E5.2); `FaceClusterer`, `FaceItem(faceId: Long, vector: QuantizedVector, personId: Long?, personConfirmed: Boolean)`, `ClusterConstraints(cannotLink: Set<Pair<Long, Long>>)` (E5.1); `FACE_VERSION = 1`.
- **Requirements:**
  1. **Detection:** ML Kit bundled face detection with landmarks `ALL`, performance mode `FAST`, minimum face size 0.1, run on the pipeline's shared 1024 px bitmap. Faces whose box short side is under 80 px or that are missing either eye landmark are skipped.
  2. **Alignment:** a similarity transform mapping the two eyes to the template coordinates for the approved model (stored in the manifest field `alignTemplate`), output 112x112.
  3. **Embedding:** an L2-normalized vector of the manifest `outputDim`, quantized with `VectorQuantizer` (`QuantizedVector` length equals that dimension), and stored in `face` with `model_version`. Set the photo's `face_version = FACE_VERSION`.
  4. **Assignment:**
     - Each new face goes to the person whose centroid has the highest cosine, if that cosine is at least `assignMin` and no `cannot_link(face, person)` exists. Otherwise it is left unassigned.
     - The centroid is the normalized mean of member vectors, recomputed for affected persons after each batch.
  5. **Reclustering (`FaceReclusterWorker`):** unique daily work, charging and idle, run after indexing. DBSCAN over all faces, with cosine distance `eps = 1 - clusterMin` and `minPts = 3`. It must keep these rules:
     - Faces of `user_confirmed` persons keep their person.
     - A new cluster sharing at least 50% of its faces with an existing unconfirmed person keeps that person's id.
     - Cannot-link pairs are never joined.
     - Noise faces stay unassigned.
     - If over 30,000 faces exist, or a run exceeds 5 minutes, recluster only the unassigned faces against each other.
  6. **Hub visibility:** a person appears in the hub only with at least 3 faces. The cover is the face with the largest box.
  7. **Initial `face_thresholds.json`:** `assignMin 0.55`, `clusterMin 0.55`. `tune.py` tunes these for purity of at least 0.96 on the 70% split.
  8. **Feature gate:** the face stage runs only when `Features.people` is true.
- **Validation:**
  - `./gradlew :core:test --tests "*FaceClusterer*"` passes:
    - Synthetic: 3 tight clusters of 10 vectors plus 5 noise vectors give 3 persons and 5 unassigned.
    - A confirmed person's faces are never moved.
    - A cannot-link face is never assigned to that person.
    - Stable ids: rerunning on the same input yields the same person ids.
  - Instrumented: seeded face photos (consented or public-domain from the labeled set) produce `face` rows with boxes inside 0 to 1, and faces under 80 px are skipped.
  - `QualityEvalTest` with `runQualityEval=true` reports weighted purity (clusters with at least 3 faces) of at least 0.95 on `api34` and on the Realme (H4).
  - **DEVICE (H4):** reclustering 20,000 synthetic face vectors (debug seeding command) finishes in under 5 minutes.

#### T3.5 People and Pets hub
- **Depends on:** T3.4, T2.7. **PRD:** 4.4.
- **Touches:** `:app` `ui/people/`; `Features.people = true`.
- **Requirements:**
  1. **Hub:**
     - A grid of persons with a circular face crop and the name, or "Add name".
     - A "Pets" tile first, which opens the PETS category results.
     - Unnamed persons sort after named ones, and both sort by face count descending.
  2. **Person detail:**
     - A photo grid of the person.
     - Actions:
       - Rename (sets `user_confirmed = 1`).
       - Merge with another person (multi-select; faces move to the target; the target is confirmed).
       - "Not this person" on a face (sets `person_id = null` and inserts `cannot_link`).
       - "Hide from memories" (creates `ExclusionRule.Person`).
  3. **Empty state** (no persons yet): "People will appear here after your photos are indexed." While indexing is running, add "X of Y photos checked so far."
  4. **Readiness:** add the `ReadinessItem` "People: after your photos are analyzed", which becomes "People: ready" after the first recluster (T2.7 card).
- **Validation:** Compose UI and instrumented tests:
  - Rename, then run `FaceReclusterWorker`: the name is kept.
  - Merge two persons: one remains, with the face count summed.
  - "Not this person", then recluster: that face is not back in that person.
  - "Hide from memories" creates the rule, and today's memories exclude photos with that person.

#### T3.6 Exclusion rules
- **Depends on:** T3.5, T1.10. **PRD:** 4.5.
- **Touches:** `:app` `ui/settings/exclusions/`; `:engine` `MemoryService` (passes rules and the per-photo person set).
- **Requirements:**
  1. **Settings, "Memory exclusions"** lists rules and lets the user add:
     - A folder, picked from distinct `relative_path` values with counts.
     - A date range (Material date range picker).
     - A person (picked from the hub list).

     Each rule can be deleted.
  2. **Scope:** rules apply to `MemoryService.today()`, `trips()`, and `forWidget()` for every source. They do not apply to the grid or search (4.5).
  3. The person set per photo is loaded with one query (a join of `face` to `person`) per memory computation, not per photo.
- **Validation:**
  - Unit tests on `MemoryService` with fakes: folder, date, and person rules each remove the matching photos from `today()` and `forWidget()`, and leave `SearchService` results unchanged.
  - Compose UI test: add and delete each rule type.

#### T3.7 Memories screen v2 (trips and events)
- **Depends on:** T3.3, T3.6. **PRD:** 4.2.
- **Touches:** `:app` `ui/memories/`; `:engine` `MemoryService`.
- **Requirements:**
  1. **Sections, in order:**
     1. "On This Day" (existing cards).
     2. "Trips": `trips()` anniversaries, for example "Trip to Lisbon, 2 years ago", then recent trips, for example "Trip to Lisbon, last week".
     3. "From the past": event anniversaries within plus or minus 3 days, with at least `MIN_EVENT_PHOTOS_FOR_MEMORY` eligible photos, not part of a trip. Title is the place name if known, plus "N years ago".
  2. **Stories:**
     - A trip story (`memory_key="trip:<tripId>"`) shows up to 20 eligible photos spread evenly over the trip's time span.
     - An event story (`"event:<eventId>"`) shows up to 15.
  3. All titles come from string resources (plurals for years). Country names come from `CityIndex.countryName` (English in v1).
  4. Add the TRIPS chip to search (shows trip cover photos; tapping opens the trip story). Before the first event rebuild, or with no trips found yet while indexing runs, tapping it shows "Trips appear after your photos are analyzed" instead of an empty list.
  5. **Readiness:** add the `ReadinessItem` "Trips: after your photos are analyzed", which becomes "Trips: ready" after the first event rebuild (T2.7 card).
- **Validation:**
  - Instrumented: seeded trip photos dated 2 years ago today show a "Trip to Lisbon, 2 years ago" card, and its story shows at most 20 photos in time order.
  - An event with 7 photos is not shown; with 8 it is.
  - Unit test of the even-spread selection: 100 photos, 20 picked, first and last included.

#### T3.8 Label export and import
- **Depends on:** T3.6, T3.11. **PRD:** 4.7, 5.4.
- **Touches:** `:data` `labels/LabelExporter.kt`, `labels/LabelImporter.kt`; `:app` Settings entries.
- **Requirements:**
  1. **Export:** writes JSON through `ACTION_CREATE_DOCUMENT`, with default name `memories-labels-<yyyy-MM-dd>.json`. Schema `version: 1` contains:
     - `photos`: fingerprint plus `hidden`, `notJunk`, and `favoriteLocal`, only for rows where any of those is true.
     - `persons`: name plus faces, each as a fingerprint and normalized box.
     - `exclusions`: folder and date rules as-is; person rules referenced by person name.
     - `albums`: every `USER` album (including pinned ones) as `{ title, cover: fingerprint | null, photos: [fingerprint] }`.
     - `removedDefaults`: `DefaultAlbum` names.
     - `rotationHistory`: `[{ key, publishedUtc }]`, so suggestions do not repeat on a new phone.
  2. **Photo fingerprint:** `displayName|dateTakenUtcMillis or "null"|sizeBytes`, because media ids change across devices and reinstalls.
  3. **Import** (`ACTION_OPEN_DOCUMENT`):
     - Photo flags are applied to matching fingerprints immediately.
     - Albums are created as `USER` albums with their matched photos (unmatched photos count toward M). `removedDefaults` is merged into the current set. History keys are inserted, ignoring ones already present.
     - Person names are stored as pending and applied once faces are indexed: a face on the matching photo with box IoU at least 0.5 gives its person that name and `user_confirmed = 1`.
     - Unmatched entries are counted and reported: "Imported N items. M photos were not found on this phone."
  4. Unknown fields are ignored. A `version` greater than 1 is rejected with "This file was made by a newer version of Memories."
- **Validation:** instrumented tests:
  1. Export, clear the database, re-sync, then import: hidden, not-junk, and favorites are restored.
  2. Then run the face stage: person names are restored.
  3. A file with `version: 2` shows the rejection message.
  4. A file with one unknown photo reports `M = 1`.
  5. Export with one user album of 3 photos, a removed Screenshots album, and 2 history keys; clear the database, re-sync, import: the album has 3 photos, Screenshots stays removed, and a candidate with either key is never published.

#### T3.9 Place reminder lines
- **Depends on:** T1.13, T3.3, T3.7. **PRD:** 4.6.
- **Touches:** `:engine` `reminder/PlaceCandidateSource.kt`.
- **Requirements:**
  1. **`PlaceCandidateSource`:** for each trip anniversary from `MemoryService.trips()` (not recent trips), create a `PLACE` candidate with `placeName` from the trip's `TripTitle` (city name; a single country name; for several countries, the first one), `yearsAgo`, `memory_type=trip`, `memory_key="trip:<tripId>"`, and the trip's cover. When several fit, keep the most recent.
  2. Exclusion rules (T3.6) apply, because `trips()` already applies them. A trip with no eligible photos gives no candidate.
  3. Register it in the `ReminderCandidateSource` set. Place lines are `sensitive`, so the lock screen shows "A memory is waiting".
- **Validation:**
  - Instrumented: seeded Lisbon trip photos dated 2 years ago this week, then rebuild, then the memory `ReminderWorker`: the title is "That time in Lisbon" or "Back in Lisbon", and tapping opens the trip story.
  - The same seed plus On This Day photos with a nature theme: the place line still wins.
  - With an exclusion rule covering the trip's dates: no place line.
  - The posted notification's public version title is "A memory is waiting".

#### T3.10 Rotating albums
- **Depends on:** T3.3, T3.5, T3.6, T2.10, T1.15. **PRD:** 4.7 (Rotating albums).
- **Touches:**
  - `:core` package `albums` (`CandidateKind`, `AlbumTitle`, `AlbumCandidate`, `AlbumCurator`, `RotationPlanner`).
  - `:engine` package `albums` (`CandidateCollector`, `AlbumRotationWorker`, `AlbumScheduler`).
  - `:data` `AlbumRepository` (rotating albums, `pin`), `rotation_history` DAO, `AlbumSettings.lastRotationKind`.
  - `:app` `ui/albums/` ("Suggested for you" section, Pin and Remove).
- **Produces:** `CandidateKind`, `AlbumTitle`, `AlbumCandidate`, `AlbumCurator`, `RotationPlanner` (E5.1); `AlbumRotationWorker`, `AlbumScheduler` (E5.2).
- **Requirements:**
  1. **Candidates (`CandidateCollector`):** built from eligible photos (`Eligibility` with exclusion rules and the per-photo person set, loaded once as in T3.6). Keys use names and local dates, never row ids, because trip and person ids change on rebuilds, reclusters, and new phones.
     - `TRIP`: one per `trip` row, with the photos of its events. Key `trip:<start yyyy-MM-dd>:<end yyyy-MM-dd>` (local dates). Title `Trip(tripTitle, startYear)`, shown as "Trip to Lisbon, 2024".
     - `PERSON_YEAR`: one per named person and local calendar year of their photos. Key `person:<name, trimmed and lowercased>:<yyyy>`. Title `PersonYear(name, year)`, shown as "Ana in 2023". Unnamed persons are skipped.
     - `PETS_YEAR`: photos with `is_pet = 1`, per local calendar year. Key `pets:<yyyy>`. Title `PetsYear(year)`, shown as "Pets in 2023".
     - `endUtcMillis` is the latest photo's effective time.
  2. **`RotationPlanner.next`:**
     1. Drop candidates with fewer than `MIN_PHOTOS` (30) photos or whose key is in `publishedKeys`.
     2. Kinds take turns in `CandidateKind` order, starting after `lastKind` (`TRIP` when `null`) and skipping kinds with no candidate left.
     3. Within the chosen kind, pick at random among candidates with `endUtcMillis <= nowUtcMillis - 365 days`; if none, among all of that kind.
     4. Return `null` if nothing is left.
  3. **`AlbumCurator.pick`:**
     1. Sort by effective time, then `mediaId`.
     2. Rank photos by: favorite first, then `faceCounts[id] > 0`, then earlier time.
     3. Near-duplicates: walking in order, when a photo is within 2,000 ms of the last kept photo, keep the higher ranked of the two.
     4. If at most `max` remain, return them. Otherwise split them into `max` contiguous slices whose sizes differ by at most 1, and take the highest ranked photo of each slice.

     It is deterministic (no randomness).
  4. **Rotation step (`AlbumRotationWorker`):** in one transaction:
     1. Insert a `ROTATING` album with `title` holding the `AlbumTitle` as kotlinx-serialization JSON (formatted by `:app` with string resources), `published_utc = now`, and `candidate_key`.
     2. Insert its `album_photo` rows (positions in time order) and its `rotation_history` row, and set `lastRotationKind`.
     3. If more than `MAX_ACTIVE` (5) `ROTATING` albums exist, delete the one with the oldest `published_utc`.

     When `next` returns `null`, nothing changes.
  5. **Scheduling:** `AlbumScheduler.ensureScheduled()` runs at app start and enqueues unique periodic work `album-rotation` every `INTERVAL_DAYS` (2 days, `KEEP`), with no charging or idle constraint. The first run happens right away. A missed period runs once; runs never pile up.
  6. **Frozen:** a published album's rows never change. At read time, the T1.15 contents rule plus `Eligibility` (exclusion rules) skip photos that became unavailable or excluded.
  7. **Pin:** "Pin to your albums" sets `kind = USER` and writes the title as plain text in the current language. `candidate_key` stays, so it never returns as a suggestion. **Remove:** deletes the album; its history row stays.
  8. **UI:** the "Suggested for you" section (T1.15) with an overflow on each tile: "Pin to your albums" and "Remove". Inside, selection mode has Select all, Add to album, Share, Hide, and Trash.
  9. **Speed:** a worker run finishes in under 1,000 ms with 50,000 photos, never loads `EmbeddingIndexHolder` or any model, and logs `ALBUM_ROTATION_DONE ms=<n>` (no other content).
- **Validation:**
  - `./gradlew :core:test --tests "*RotationPlanner*" --tests "*AlbumCurator*"` passes:
    - A candidate with 29 photos is never picked; with 30 it is.
    - A candidate whose key is published is never picked.
    - With `lastKind = TRIP` and both a person-year and a trip left, the person-year is picked. With no person-year, pets is picked.
    - Given one candidate older than a year and one from last month in the same kind, the older one is picked for every seed.
    - No eligible candidates gives `null`. The same seed gives the same pick.
    - Curator: 300 photos give 50, including one from the first and one from the last slice; a favorite wins its slice.
    - Two photos 1,500 ms apart keep only the favorite; 2,500 ms apart keep both. 40 photos give 40. Same input gives the same output.
  - Instrumented (`work-testing`):
    - Seed a 35-photo trip and a named person with 40 photos in 2023: the first run publishes the trip, the second the person-year.
    - With 6 eligible candidates, 6 runs leave 5 rotating albums, and the first published is gone.
    - A run with no candidates left changes nothing.
    - Pin: the album becomes `USER` with a plain title, and the next run publishes a new album without removing anything.
    - Remove: the album is deleted and never republished.
    - Hiding a photo in a rotating album, or adding an exclusion rule for a person in it, skips those photos while the stored list stays the same.
    - Rerunning `EventRebuildWorker` (new trip ids) does not make a published trip eligible again.
    - A run in a fresh process leaves `EmbeddingIndexHolder` unloaded and loads no LiteRT model.
  - **DEVICE (H4):** with 50,000 seeded photos, `ALBUM_ROTATION_DONE ms` is under 1,000.

#### T3.11 Rediscover
- **Depends on:** T3.10. **PRD:** 4.7 (Rediscover).
- **Touches:** `:core` `albums/RediscoverPicker.kt`; `:engine` `albums/RediscoverPool.kt`, `AlbumRotationWorker` (adds the Rediscover step); `:data` `AlbumSettings.rediscoverCycle`, `photo.rediscover_cycle`; `:app` the Rediscover tile in Collections.
- **Produces:** `RediscoverPicker` (E5.1).
- **Requirements:**
  1. **Pool (`RediscoverPool`):** eligible photos (`Eligibility` with exclusion rules) minus photos that are in an event with a non-null `trip_id`, show a named person who has at least 30 eligible photos, have `is_pet = 1`, or are in a current `ROTATING` album. Ordinary events never count as grouped.
  2. **`RediscoverPicker.pick`:** `unseen` is the pool where `lastShownCycle[id]` (default 0) is below `cycle`.
     - If `unseen` has at least `max` photos, pick `max` at random from it and keep `cycle`.
     - Otherwise take all of `unseen`, move to `cycle + 1`, and fill the rest at random from the pool minus those already taken.
     - An empty pool gives an empty result and the same `cycle`.
  3. **Rediscover step:** runs in every `AlbumRotationWorker` run, after the rotation step. It creates the single `REDISCOVER` album row on first use, replaces its `album_photo` rows with the pick, sets `rediscover_cycle` on the picked photos to the returned cycle, and stores the cycle in `AlbumSettings`.
  4. While `REDISCOVER` is in `removedDefaults`, the step is skipped. Restoring it enqueues a one-time `album-rotation` run, so a fresh set appears right away.
  5. The tile is `DefaultAlbum.REDISCOVER` in Collections, shown once it has photos. The T3.10 speed budget covers both steps.
- **Validation:**
  - `./gradlew :core:test --tests "*RediscoverPicker*"` passes:
    - Pool of 120, cycle 1: 50 photos, all unseen.
    - After marking two picks of 50: the third pick has the 20 unseen photos plus 30 others, and returns cycle 2.
    - An empty pool gives an empty result. The same seed gives the same output.
  - Instrumented: seed a trip, a named person with 30 photos, a pet photo, and 60 plain photos in small events (including one ordinary event of 20 photos). The Rediscover album has 50 photos, all from the 60 plain ones. The next run has the 10 unseen plain photos plus 40 others.
  - Removing the Rediscover tile skips the next refresh; restoring it produces a new set.

#### Gate G3
- [ ] T3.1 to T3.11 merged. All instrumented suites pass. Room migrations (if the schema changed) are tested.
- [ ] Face purity at least 0.95 on `api34` and the Realme. The junk and search targets still pass.
- [ ] DEVICE: event rebuild under 60 s, recluster under 5 min, and album rotation under 1 s.
- [ ] Tag `v0.3.0`. Upload to the closed test track.
- [ ] `FEATURES.md` matches the plan (`python -I tools/check-features.py` passes), and every feature whose tasks are all done reads accurately.
- [ ] Commit the tracker: `Phase 3 complete: tracker update`.

---

### Phase 4: Widget Studio & Vault

#### T4.1 Widget Studio
- **Depends on:** T1.11, T1.15, T3.5, T3.6. **PRD:** 4.3, 4.7.
- **Touches:** `:widget` `config/WidgetConfigActivity.kt`, widget info XML; `:engine` `MemoryService.forWidget` (all sources); `Features.widgetStudio = true`.
- **Produces:** the remaining `WidgetSource` variants (E5.1).
- **Requirements:**
  1. **Widget info:**
     - `android:configure` points to `WidgetConfigActivity`.
     - `android:widgetFeatures="reconfigurable|configuration_optional"`. On API 31+, adding a widget uses the On This Day default without opening the activity. On API 29 and 30, the activity opens on add.
  2. **Options:**
     - Source: On This Day, a person (picker), Pets, Favorites, a phone folder (folder picker), or one of your albums (album picker, `USER` albums only).
     - Refresh: Hourly, Every 6 hours, or Daily.
     - Corner radius: 0, 8, 16, or 24 dp.
     - Border: None, Thin (2 dp), or Thick (6 dp).
     - "Use wallpaper colors" (API 31+ only; hidden below).
  3. **Preview:** a live Compose preview approximating the widget.
  4. **Save:** writes `widget_config`, replaces that widget's periodic work, and updates the widget.
  5. **`forWidget` per source:** at most 5 eligible photos (eligibility and exclusions apply), shuffled with the date seed.
     - Person: photos containing that person.
     - Pets: the PETS category.
     - Favorites: MediaStore favorites or local favorites.
     - Folder: photos whose `relativePath` starts with the folder path.
     - User album: the album's photos. Eligibility and exclusion rules apply here even though they do not apply inside the album (PRD 4.7).

     Labels:
     - On This Day: "N years ago".
     - Other sources: the photo's month and year, for example "March 2023".
  6. **Empty user album:** if a user album source has no eligible photos, show "This album has no photos to show. Tap to choose another." Tapping opens the config activity.
  7. **Merges:** when persons are merged (T3.5), every `widget_config` row with source `PERSON` and the merged-away id is updated to the target id in the same transaction. Add this to the merge function.
  8. **Missing source:** if the person was deleted, or the folder or user album no longer exists, show "This widget's source is gone. Tap to choose another." Tapping opens the config activity.
- **Validation:**
  - Glance unit tests for each source label, each border, and the missing-source state.
  - Instrumented:
    - Configure a Person source, then merge that person into another: the widget follows the merge target (the merged-away id maps to the target).
    - Delete the person: the missing-source state shows.
    - A user album source whose only photos are covered by an exclusion rule shows the empty-album state; deleting that album shows the missing-source state.
    - Changing the refresh to Hourly replaces the work with a 1 h period (checked through `WorkManager.getWorkInfosForUniqueWork`).
  - **DEVICE (H4):** configure and reconfigure on the Realme launcher.

#### T4.2 Vault encryption core
- **Depends on:** T1.3. **PRD:** 4.5, 10.6.
- **Touches:** `:vault` `crypto/VaultKeyProvider.kt`, `crypto/VaultCipher.kt`, `crypto/VaultFileFormat.md` (format doc).
- **Produces:**
  - `VaultKeyProvider` (interface: `key(): SecretKey`).
  - `KeystoreVaultKeyProvider` (real) and `TestVaultKeyProvider` (androidTest only; a Keystore AES key without user authentication).
  - `VaultCipher.encrypt(input: InputStream, output: OutputStream)` and `VaultCipher.decrypt(input: InputStream): InputStream`.
  - `VaultCorruptException`.
- **Requirements:**
  1. **The key:**
     - Android Keystore, AES-256 GCM, alias `vault_master_v1`, user authentication required.
     - On API 30+, `setUserAuthenticationParameters(300, AUTH_BIOMETRIC_STRONG | AUTH_DEVICE_CREDENTIAL)`. On API 29, `setUserAuthenticationValidityDurationSeconds(300)`.
     - `setInvalidatedByBiometricEnrollment(false)`.
     - StrongBox when available, falling back to the regular Keystore if `StrongBoxUnavailableException` is thrown.
  2. **File format:**
     - Header: ASCII `MGV1`, then 1 byte format version (1), then 4 bytes chunk size big-endian (1,048,576), then 16 random bytes of file id.
     - Then chunks, each a 12-byte IV (from the Keystore cipher), the ciphertext, and a 16-byte tag.
     - Each chunk's AAD is file id, then chunk index (8 bytes big-endian), then last-chunk flag (1 byte).
     - An empty file is one final chunk with empty plaintext.
  3. **Decrypt:**
     - Verifies the header, the chunk order, and that the last flag appears exactly once at the end.
     - Any failure throws `VaultCorruptException`.
     - Plaintext is streamed. It is never written to disk.
  4. **Key loss:** if the key is permanently invalidated (the screen lock was removed), `key()` throws `VaultKeyLostException`. The UI (T4.3) shows "Your vault can no longer be opened because the screen lock was removed."
- **Validation:** `./gradlew :vault:ciGroupDebugAndroidTest` (with `TestVaultKeyProvider`) passes:
  - Round trip for sizes 0, 1, 1,048,576, 1,048,577, and 25 MiB.
  - Flipping one byte in chunk 2 throws.
  - Truncating the last chunk throws.
  - Swapping chunks 1 and 2 throws.
  - A wrong magic number throws.
  - Two encryptions of the same input produce different files.

#### T4.3 Vault repository, flows, and UI
- **Depends on:** T4.2, T1.9. **PRD:** 4.5, 10.6. **Human:** H9.
- **Touches:**
  - `:vault` `VaultRepository.kt`.
  - `:app` `ui/vault/`, the viewer "Move to vault" action, Settings entry, and `BiometricGate`.
  - `Features.vault = true`.
- **Produces:** `VaultRepository` (E5.2).
- **Requirements:**
  1. **Storage:** vault files live in `noBackupFilesDir/vault/` as `<uuid>.mgv` and `<uuid>.thumb` (a 512 px JPEG thumbnail, encrypted in the same format).
  2. **`moveIn(ids)`:**
     1. For each photo, encrypt a copy, decrypt it again, and check that its SHA-256 equals the original's. Only then insert `vault_item`.
     2. Then issue one `MediaStore.createDeleteRequest` for all originals on API 30+ (a permanent delete, not trash). On API 29, delete each with the `RecoverableSecurityException` flow.
     3. If the user cancels the delete, remove the vault copies and rows and show "Nothing was moved."
     4. If an item fails to encrypt, skip it and report "N photos could not be moved."
  3. **Export (`exportAll()`, and export selected):**
     1. Decrypt into MediaStore `Pictures/Memories Restored/` using `IS_PENDING`, keeping the original display name and setting `DATE_TAKEN` from the stored value.
     2. Verify the written size, then delete the vault file and its row.
  4. **Unlock (`BiometricGate`):**
     - On API 30+, `BiometricPrompt` with `BIOMETRIC_STRONG | DEVICE_CREDENTIAL`.
     - On API 29, `BiometricPrompt` with `BIOMETRIC_STRONG` and a "Use PIN" negative button that launches `KeyguardManager.createConfirmDeviceCredentialIntent`.
     - A `UserNotAuthenticatedException` during a key operation re-prompts.
  5. **First-use warning:** a dialog that requires checking "I understand". It covers:
     1. Vault photos are deleted if the app is uninstalled or its data is cleared.
     2. Removing the screen lock makes the vault unreadable.
     3. Export is available anytime from the vault menu.
  6. **Screen protection:**
     - `FLAG_SECURE` on all vault screens.
     - On `ON_STOP`, decrypted bitmaps are cleared and the vault returns to its locked screen.
     - If `KeyguardManager.isDeviceSecure` is false, show "Set a screen lock to use the vault." and do not create the key.
  7. **Exclusion everywhere:** vault photos never appear in the grid, search, memories, or widgets. They are no longer in MediaStore, and tests confirm it.
- **Validation:**
  - Instrumented, with `TestVaultKeyProvider` and UiAutomator for the system dialog:
    - Move 3 seeded photos in: 3 `vault_item` rows, the originals gone from MediaStore after sync, and none in search, the grid, or `forWidget`.
    - Cancel the system delete dialog: no vault rows, no vault files.
    - Export all: 3 photos in `Pictures/Memories Restored/` with the original names and dates, and the vault empty.
    - SHA-256 of each exported file equals the original's.
    - Simulated `VaultKeyLostException`: the lost-key message shows.
  - **DEVICE (H9)** on the Realme with the real key:
    1. Unlock with a fingerprint.
    2. Add a new fingerprint in system settings.
    3. Unlock again with the old fingerprint and with the PIN: the vault opens and photos decrypt.
    4. A screenshot attempt on the vault screen shows a blank or blocked image.

#### Gate G4
- [ ] T4.1 to T4.3 merged. All suites pass.
- [ ] H9 passed and recorded.
- [ ] Tag `v0.4.0`. Upload to the closed test track. The closed test has had at least 12 testers for 14 continuous days (R4) by now, or the remaining days are known.
- [ ] `FEATURES.md` matches the plan (`python -I tools/check-features.py` passes), and every feature whose tasks are all done reads accurately.
- [ ] Commit the tracker: `Phase 4 complete: tracker update`.

---

### Phase 5: Optimization & Polish

#### T5.1 Low-end device validation and low-RAM policy
- **Depends on:** G4. **PRD:** 5.1, 5.2, 8. **Human:** H10.
- **Touches:** `:engine` `DevicePolicy.kt`; `:app` Settings toggle.
- **Requirements:**
  1. **`DevicePolicy.isLowEnd`** is true if `ActivityManager.isLowRamDevice()` is true or `totalMem` is under 4 GiB. A debug-only setting (present only when `BuildConfig.DEBUG`) forces it on, so the low-end code paths can be exercised on the Realme. Speed targets are still measured on the low-end device.
  2. **On low-end devices:**
     - Analysis decodes at 768 px.
     - Batches are 100 photos.
     - The face stage is off by default, behind a Settings toggle "Find people in photos (uses more battery while charging)".
  3. **Low-end acceptance (proposed; the human confirms in the Decisions log before measuring):**
     - Indexing at least 2 photos per second.
     - Search end to end under 600 ms at 50,000 photos.
     - No OOM or ANR during a 5,000-photo overnight run.
     - First library scan of 50,000 photos under 60,000 ms (`SYNC_DONE ms`).
     - Each reminder run under 2,000 ms with 50,000 photos (`REMINDER_DONE ms`).
  4. **Support floor:** the low-end device is a 2018 phone on Android 10 (PRD 5.1).
- **Validation:**
  - Unit test of `DevicePolicy` with fake memory values (3 GiB gives low-end, 6 GiB does not).
  - On the Realme with the debug override on: analysis decodes at 768 px, batches are 100, and the face stage is off until the toggle is set.
  - **DEVICE (H10):** the microbenchmarks, `searchService50k`, the first scan, the reminder runs, and the overnight run on the low-end device meet the confirmed targets. Results are recorded with the device model and year.

#### T5.2 Performance and stability polish
- **Depends on:** G4. **PRD:** 5.2, 5.8.
- **Requirements:**
  1. Regenerate baseline profiles for startup, grid scroll, search, and story launch.
  2. In debug builds, add LeakCanary and StrictMode (disk and network on the main thread, leaked closables) with `penaltyLog`. Fix every reported leak or violation in the main flows.
  3. **Main-thread rule:** no main-thread database or disk access anywhere. Enforce it with StrictMode `penaltyDeath` in an instrumented test variant.
- **Validation:**
  - **DEVICE (H4)** on the Realme:
    - Cold start under 1,000 ms.
    - Grid P90 under 12 ms and P99 under 24 ms.
    - Story launch under 1,000 ms.
  - The instrumented StrictMode-death suite passes over onboarding, grid, viewer, search, people, and vault.
  - LeakCanary reports no leaks in a scripted walkthrough.
  - Release-log privacy check (10.4): install the release build on `api34`, seed photos with distinctive names (for example `zz_canary_photo.jpg` in `Pictures/zz_canary/`), run the scripted walkthrough including a search for "zz canary query", and confirm `adb logcat -d` contains none of `zz_canary` or `canary query`.

#### T5.3 Accessibility
- **Depends on:** G4. **PRD:** 10.8.
- **Requirements:**
  1. **Content descriptions:**
     - Every photo has one built from its date, for example "Photo, March 10, 2024".
     - Every icon button has a label.
     - Decorative images use `null`.
  2. **Touch targets:** at least 48 dp. Text and icon contrast is at least 4.5:1, or 3:1 for large text.
  3. **Large text:** at font scale 2.0 and the largest display size, primary actions stay visible and clickable. The widget label is allowed to truncate with an ellipsis.
  4. **TalkBack order** on the story viewer: a "Next" and "Previous" accessibility action plus "Pause".
- **Validation:**
  - Compose UI tests at font scale 2.0 assert primary actions are displayed and clickable on every screen.
  - An automated accessibility check (`enableAccessibilityChecks` in Espresso, or the Compose equivalent) passes.
  - **DEVICE (H4):** a TalkBack walkthrough of onboarding, grid, viewer, search, memories, story, and vault, plus an Accessibility Scanner pass with no errors. Recorded in `docs/accessibility-audit.md`.

#### T5.4 Localization readiness
- **Depends on:** G4. **PRD:** 5.9, 7.
- **Requirements:**
  1. Enable pseudo-locales (`en-XA`, `ar-XB`) in debug builds.
  2. All layouts must work right-to-left (RTL).
  3. The human chooses the launch languages. If any are chosen, add `values-<lang>/strings.xml`. Search stays English, and the empty-results copy says so.
- **Validation:**
  - Compose UI tests under `ar-XB` (RTL) and `en-XA` (long strings) on every screen: no clipped primary actions.
  - Lint `MissingTranslation` is an error for the chosen languages.

#### T5.5 Final quality, battery, and launcher matrix
- **Depends on:** T5.1 to T5.4. **PRD:** 5.7, 10.5, 10.7.
- **Validation:**
  - **Quality:** `QualityEvalTest` on the Realme meets all four 5.7 targets.
  - **Battery (DEVICE):**
    - 30 minutes of "Index now" off the charger: record the battery percentage drop.
    - An overnight run off the charger after indexing completes: the app's share in Android's battery usage screen is at most 1%.
  - **Launchers:** widget add, configure, tap, and refresh on Pixel Launcher (`apiLatest` emulator), the Realme launcher, and Nova Launcher on the Realme. Results go in `docs/launcher-matrix.md`.
  - **Android 10:** a full manual smoke test on `api29` covering every main flow.

#### Gate G5
- [ ] T5.1 to T5.5 merged and recorded.
- [ ] Tag `v1.0.0-rc1`.
- [ ] `FEATURES.md` matches the plan (`python -I tools/check-features.py` passes), and every feature whose tasks are all done reads accurately.
- [ ] Commit the tracker: `Phase 5 complete: tracker update`.

---

### Release Track (runs alongside the phases)

#### R1 Play Console and upload key (HUMAN, H6; after G1)
- **Requirements:**
  1. Create the Play Console app "Memories" with package `dev.build_with_mhonn.memories`, and enable Play App Signing.
  2. Generate the upload key with `keytool`: RSA 4096, validity of 30 years.
  3. Store the key file and passwords in a password manager plus one offline copy.
  4. Add these four properties to `~/.gradle/gradle.properties` (never the repo): `MEMORIES_UPLOAD_STORE_FILE`, `MEMORIES_UPLOAD_STORE_PASSWORD`, `MEMORIES_UPLOAD_KEY_ALIAS`, `MEMORIES_UPLOAD_KEY_PASSWORD`.
- **Validation:** the human confirms. The agent never sees the key or passwords.

#### R2 Signing, versioning, and release procedure (agent; after R1)
- **Touches:** `app/build.gradle.kts`, `docs/RELEASING.md`, `docs/release-notes/`.
- **Requirements:**
  1. **Signing:** the release signing config reads the four R1 properties. If any is missing, the release build is unsigned and Gradle prints a warning. It does not fail, so CI keeps working.
  2. **Versioning:** `VERSION_CODE` increases by exactly 1 per Play upload. `VERSION_NAME` follows `MAJOR.MINOR.PATCH` (11.4). Every upload has a matching git tag `v<VERSION_NAME>` and `docs/release-notes/<VERSION_NAME>.md`, whose first 500 characters are the Play "What's new" text.
  3. **`docs/RELEASING.md`** has the exact steps:
     1. Bump the version.
     2. Run the release gates (11.2).
     3. `./gradlew :app:bundleRelease`.
     4. Verify with `bundletool`.
     5. Upload to the target track.
     6. Tag.
- **Validation:**
  - With a throwaway keystore generated in a temp folder, `bundleRelease` produces an `.aab` that `apksigner verify` accepts (after `bundletool build-apks --mode=universal`).
  - Without the properties, `bundleRelease` succeeds unsigned.
  - The first internal-testing upload (by the human) is accepted by Play.

#### R3 Privacy policy, README, and store listing drafts (agent; before R4)
- **Touches:** `docs/privacy-policy.md` (served by GitHub Pages at `https://mhonnczedz1.github.io/memories_gallery_app/privacy-policy`), `README.md`, `docs/store-listing.md`, `docs/screenshots/`.
- **Requirements:**
  1. **Privacy policy:** states that no data is collected, shared, or transmitted; the app has no internet permission; backup is disabled; processing is on-device only; and gives contact by email (`mhonn.ybanez@gmail.com`).
  2. **README "Privacy design":** how to verify it yourself, either `./gradlew :app:verifyPrivacy` or `apkanalyzer manifest permissions app-release.apk` on a downloaded APK, plus how the privacy tests work.
  3. **Store listing drafts:**
     - Title "Memories - Gallery App" (at most 30 characters).
     - Short description at most 80 characters.
     - Full description at most 4,000 characters.
     - Claims match the Data safety answers exactly.
     - Describes the code as "source available for inspection", never "open source" (also in the README).
     - States the support floor in plain words: "Works best on phones from 2018 or newer."
     - No em or en dashes.
  4. **Screenshots:** at least 4 phone screenshots captured from `apiLatest` with synthetic photos only. The human makes the 512x512 icon and the 1024x500 feature graphic (or approves agent drafts).
- **Validation:** the Pages URL loads (checked by the human); character counts verified by a script; screenshots contain no real personal photos.

#### R4 Closed test (HUMAN, H7; start right after G2)
- **Requirements:**
  1. Create the closed testing track and invite about 20 testers.
  2. Keep at least 12 testers opted in for 14 continuous days.
  3. Testers send feedback through the in-app feedback email.
  4. When the period ends, apply for production access in Play Console.
- **Validation:** Play Console shows the requirement met. Record the dates in the Decisions log.

#### R5 Store compliance (HUMAN, H8; before GR)
- **Requirements:**
  - [ ] **Data safety:** no data collected and none shared.
  - [ ] **Photo and video permissions declaration** (core gallery function).
  - [ ] **Foreground service type declarations** for `dataSync` and `mediaProcessing`, with the description "User-started photo indexing with a visible progress notification" and a short screen recording if requested.
  - [ ] **Content rating** questionnaire.
  - [ ] **Target audience:** not designed for children.
  - [ ] **Ads:** none.
  - [ ] **App access:** no login required.
  - [ ] **Privacy policy URL** from R3.
  - [ ] **Target API level** meets the current Play requirement.
- **Validation:** Play Console shows no blocking policy items.

#### R6 Production rollout and monitoring (HUMAN approves each step, H11)
- **Depends on:** G5, R4, R5.
- **Requirements:**
  1. **Staged rollout:** 10%, then 50%, then 100%, with at least 2 days per step.
  2. **Halt** if any of these happens: crash-free sessions under 99.5%, user-perceived ANR rate over 0.47%, or any review reporting data loss or missing photos.
  3. **Monitoring:** check vitals and reviews daily for 2 weeks after each release, then weekly.
  4. **Hotfix process (11.6):**
     1. Create a `hotfix/<version>` branch.
     2. Run the release gates.
     3. Upload to internal testing.
     4. Roll out at 20%, then 100%.
- **Validation:** `v1.0.0` reaches 100%. The monitoring log is in `docs/release-log.md`.

#### Gate GR
- [ ] R1 to R6 complete. `v1.0.0` is live at 100%.
- [ ] `FEATURES.md` matches the plan (`python -I tools/check-features.py` passes), and every feature whose tasks are all done reads accurately.
- [ ] Commit the tracker: `Release complete: tracker update`.

---

## E9. Execution Tracker

Mark `[x]` after merge, with a one-line result note (for example a measured value). Commit only at phase gates (E2.3). Status values in notes: `AWAITING HUMAN`, `BLOCKED: <reason>`.

**Phase 0**
- [ ] T0.1 Repository and workstation setup (HUMAN)
- [ ] T0.2 Project scaffold and CI
- [ ] T0.3 Privacy gates
- [ ] T0.4 License review (HUMAN approves)
- [ ] T0.5 Model conversion pipeline and model pack
- [ ] T0.6 Model runtime spike and microbenchmarks
- [ ] T0.7 Widget image limits spike
- [ ] **G0** Foundations gate

**Phase 1**
- [ ] T1.1 Core domain types and date rules
- [ ] T1.2 On This Day selection and widget picking
- [ ] T1.3 Room database schema v1
- [ ] T1.4 MediaStore sync
- [ ] T1.5 Photo access state
- [ ] T1.6 Screenshot rule
- [ ] T1.7 App shell, onboarding, permission UX
- [ ] T1.8 Photo grid
- [ ] T1.9 Photo viewer and actions
- [ ] T1.10 Memories screen, MemoryService, story viewer
- [ ] T1.11 Daily Memory widget (basic)
- [ ] T1.12 Settings, hidden album, licenses
- [ ] T1.13 Memory reminders
- [ ] T1.14 Multi-select
- [ ] T1.15 Albums tab, user albums, default albums
- [ ] T1.16 Background reliability check
- [ ] **G1** MVP gate (tag `v0.1.0`)

**Phase 2**
- [ ] T2.1 CLIP tokenizer
- [ ] T2.2 Image preprocessing
- [ ] T2.3 Final CLIP encoders and model registry
- [ ] T2.4 Vector quantization and index
- [ ] T2.5 Tier 1 junk filter
- [ ] T2.6 Indexing pipeline, scheduling, thermal guard, Index now
- [ ] T2.7 Search, categories, index status UI
- [ ] T2.8 Labeled quality set, evaluation, tuning
- [ ] T2.9 Theme reminder lines
- [ ] T2.10 Chat screenshots, pet flag, junk albums
- [ ] **G2** AI and search gate (tag `v0.2.0`, closed test starts)

**Phase 3**
- [ ] T3.1 Offline place lookup (GeoNames)
- [ ] T3.2 Event grouping
- [ ] T3.3 Home area, trips, event persistence
- [ ] T3.4 Faces: detection, embedding, clustering
- [ ] T3.5 People and Pets hub
- [ ] T3.6 Exclusion rules
- [ ] T3.7 Memories screen v2
- [ ] T3.8 Label export and import
- [ ] T3.9 Place reminder lines
- [ ] T3.10 Rotating albums
- [ ] T3.11 Rediscover
- [ ] **G3** Memories and people gate (tag `v0.3.0`)

**Phase 4**
- [ ] T4.1 Widget Studio
- [ ] T4.2 Vault encryption core
- [ ] T4.3 Vault repository, flows, UI
- [ ] **G4** Widget Studio and vault gate (tag `v0.4.0`)

**Phase 5**
- [ ] T5.1 Low-end validation and low-RAM policy
- [ ] T5.2 Performance and stability polish
- [ ] T5.3 Accessibility
- [ ] T5.4 Localization readiness
- [ ] T5.5 Final quality, battery, launcher matrix
- [ ] **G5** Polish gate (tag `v1.0.0-rc1`)

**Release track**
- [ ] R1 Play Console and upload key (HUMAN, after G1)
- [ ] R2 Signing, versioning, release procedure
- [ ] R3 Privacy policy, README, store listing drafts
- [ ] R4 Closed test, 12+ testers for 14 days (HUMAN, after G2)
- [ ] R5 Store compliance (HUMAN)
- [ ] R6 Production rollout and monitoring
- [ ] **GR** Release gate (`v1.0.0` at 100%)

### Suggested order and parallelism
- Phase 0: T0.2 then T0.3 and T0.7 in parallel. T0.4, then T0.5, then T0.6.
- Phase 1: T1.1, T1.3, and T1.5 can run in parallel. Then T1.2, T1.4, T1.6, T1.7. Then T1.8 to T1.12. Then T1.13 and T1.14 in parallel. Then T1.15 and T1.16.
- Phase 2: T2.1, T2.2, and T2.4 in parallel. Then T2.3, T2.5, T2.6, T2.7, T2.10, T2.8, T2.9.
- Phase 3: T3.1 and T3.2 in parallel with T3.4. Then T3.3, T3.5, T3.6, T3.7, T3.9, T3.10, T3.11, T3.8.
- Phase 4: T4.2 in parallel with T4.1, then T4.3.
- Release: R2 and R3 can run during Phases 2 and 3.
