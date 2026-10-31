# Memories: Feature List

What the app does once every task in [`EXECUTION_PLAN.md`](EXECUTION_PLAN.md) is complete. Requirements live in [`PRD.md`](PRD.md).

## Keeping this list aligned with the plan

- Every task ID in the execution plan tracker (E9) appears in this file at least once: under a feature, or under "Foundations" at the end.
- Every task ID in this file exists in the plan.
- When a task is added, removed, renamed, or changes what the user sees, this file is updated in the same change.
- `tools/check-features.py` checks the first two rules in CI (set up in plan task T0.2), and every phase gate rechecks them.
- A feature is **available** when every task listed under it is marked done in E9.

---

## Feature 1: Photo Gallery

**What it is:** A fast, familiar gallery for every photo and video on your phone, including the SD card. You can view, share, favorite, delete, and edit photos, and hide the ones you don't want to see.

**Example:** You open Memories for the first time. It explains that your photos never leave the phone and asks for photo access. Your whole library appears, grouped by month. You scroll back to last summer, open a photo, zoom in, and share it to a friend through your messaging app. You find a blurry duplicate and move it to the trash.

**Built from:**
| Part | What it does | Tasks |
| :--- | :--- | :--- |
| Welcome and photo access | First-run screens, the privacy promise, and clear handling when you allow all, some, or no photos | T1.5, T1.7 |
| Library sync | Finds every photo and video on the phone and SD card, and keeps up as you add or delete them | T1.4 |
| Photo grid | Month-grouped grid that scrolls smoothly, even with tens of thousands of photos | T1.8 |
| Select many photos | Long-press to select several photos, then share, hide, or trash them together | T1.14 |
| Photo viewer | Full-screen view with zoom and swipe; share, favorite, trash, edit in another app, and photo info | T1.9 |
| Hidden photos | Hide a photo from the grid, memories, widgets, and search; find it again in the Hidden album | T1.9, T1.12 |
| Settings, licenses, feedback | Privacy explanation, open-source licenses, app version, and a "Send feedback" email | T1.12 |

---

## Feature 2: On This Day

**What it is:** Every day, the app shows the photos you took on this date in past years and plays them as a full-screen story.

**Example:** On your birthday, you open the Memories tab and see "1 year ago" and "4 years ago" cards. You tap "4 years ago" and watch your party photos play one after another. You tap to skip ahead, then swipe down to close.

**Built from:**
| Part | What it does | Tasks |
| :--- | :--- | :--- |
| On This Day picker | Finds photos from this date (give or take 3 days) in past years, and handles leap days, New Year, and time zones | T1.2 |
| Memories screen | One card per year, with a title like "3 years ago" | T1.10 |
| Story viewer | Full-screen slideshow with tap to skip, press to pause, and swipe to close; skips photos that were deleted | T1.10 |

---

## Feature 3: Clean Memories (Junk Filter)

**What it is:** Screenshots, receipts, documents, and blurry shots are kept out of your memories and widgets automatically, so they only show real moments. Nothing is deleted, and you can always mark a photo "Not junk."

**Example:** Last March you photographed a lot of receipts for expenses. This year, your "1 year ago" memory shows only the hiking trip from that week. Later, you search the Documents chip to find one of those receipts.

**Built from:**
| Part | What it does | Tasks |
| :--- | :--- | :--- |
| Screenshot detection | Spots screenshots by folder, file name, and screen size; works from day one | T1.6 |
| Receipt, document, and blur detection | Uses on-device text reading and image understanding to flag receipts, documents, and blurry photos | T2.5 |
| Accuracy tuning | Tests the filter on a labeled photo set so it rarely hides a real memory | T2.8 |

---

## Feature 4: Trips & Events

**What it is:** The app notices when you were away from home, groups those photos into named trips, and brings them back on their anniversary. Busy days at home become event memories.

**Example:** Two years ago you spent a long weekend in Lisbon. Today the Memories tab shows "Trip to Lisbon, 2 years ago." You tap it and the story plays highlights from the whole trip, from the first morning to the last night.

**Built from:**
| Part | What it does | Tasks |
| :--- | :--- | :--- |
| Offline place names | A built-in world city list turns photo locations into names like "Lisbon," with no internet | T3.1 |
| Event grouping | Splits your timeline into events wherever there's a gap of more than 3 hours | T3.2 |
| Home and trip detection | Works out your home area, then finds overnight stays more than 100 km away and names them | T3.3 |
| Trip and event memories | "Trip to Lisbon, 2 years ago," recent trips, "From the past" event cards, trip stories, and the Trips search chip | T3.7 |

---

## Feature 5: Smart Search

**What it is:** Type what you remember, in plain English, and the app finds matching photos, all on the phone. Quick chips jump straight to pets, food, documents, trips, and screenshots.

**Example:** You want the photo of your friend's red car from a road trip but don't remember when it was. You type "red car with people in front" and it's in the first row of results. Another day you tap the Food chip to find that ramen place you photographed.

**Built from:**
| Part | What it does | Tasks |
| :--- | :--- | :--- |
| Understanding words | Turns your search text into a form the AI model understands | T2.1 |
| Understanding photos | Prepares each photo and turns it into a "fingerprint" of what it shows | T2.2, T2.3 |
| Fast matching | Compares your search against every photo's fingerprint in a fraction of a second | T2.4 |
| Search screen and chips | Search box, results, Pets, Food, Documents, and Screenshots chips, and the "Indexed X of Y photos" progress line | T2.7 |
| Accuracy testing | Checks that a relevant photo appears in the top 5 for most searches | T2.8 |

---

## Feature 6: People & Pets

**What it is:** Faces are grouped into people automatically, on the phone. You can name people, merge duplicates, and fix wrong matches. Pets have their own album.

**Example:** You open People and see a group of photos of your sister. You name the group, and then merge a second group that turned out to be her too. You tap "Not this person" on one photo that was wrongly included. You open Pets to scroll through every photo of your cat.

**Built from:**
| Part | What it does | Tasks |
| :--- | :--- | :--- |
| Face grouping | Finds faces, creates a face fingerprint, and groups matching faces into people; keeps your names and fixes | T3.4 |
| People hub | Name, merge, "Not this person," and "Hide from memories" | T3.5 |
| Pets album | Finds dogs and cats in your photos and shows them in a Pets tile and search chip | T2.7, T3.5 |

---

## Feature 7: Memory Widgets

**What it is:** Home screen widgets that show a memory every day. Each widget can be set to show On This Day, a person, your pets, your favorites, a phone folder, or one of your albums, with its own refresh rate and style.

**Example:** You add a widget to your home screen. Each morning it shows a photo from this day in past years, labeled "3 years ago." You tap next to see more, or tap the photo to open the story. You add a second widget set to your partner, refreshing every 6 hours, with rounded corners and your wallpaper colors.

**Built from:**
| Part | What it does | Tasks |
| :--- | :--- | :--- |
| Widget image sizing | Finds the largest photo size widgets can safely show on each phone | T0.7 |
| Daily Memory widget | On This Day photos with next and previous buttons, tap to open the story, and a daily refresh; handles deleted photos and missing access | T1.11 |
| Widget Studio | Choose the source (On This Day, person, pets, favorites, a phone folder, or one of your albums), refresh (hourly, every 6 hours, daily), and style (corners, border, wallpaper colors) | T4.1 |

---

## Feature 8: Private Vault

**What it is:** A locked, encrypted space for sensitive photos, opened with your fingerprint or PIN. Vault photos are removed from the gallery and never show up in search, memories, or widgets. You can export them back anytime.

**Example:** You have photos of your passport and medical documents. You select them, tap "Move to vault," read the warning, and confirm. They disappear from the gallery. Later, you unlock the vault with your fingerprint to show your passport photo at an airport counter.

**Built from:**
| Part | What it does | Tasks |
| :--- | :--- | :--- |
| Vault encryption | Encrypts each photo with a key protected by the phone's secure hardware; detects tampering | T4.2 |
| Vault screens and flows | Move in (only after checking each copy), unlock, view, export back to the gallery, the warning dialog, and screenshot blocking | T4.3 |

---

## Feature 9: Privacy Controls

**What it is:** Privacy you can check. The app has no internet access at all, nothing is backed up to the cloud, and the code is published for anyone to inspect. You also control what can appear in your memories, and can back up your labels to a file.

**Example:** You don't want photos from a past relationship resurfacing. You add an exclusion rule for that date range, and they stop appearing in memories and widgets, while staying in your gallery. Before switching phones, you export your labels (people's names, hidden photos, rules) to a file and import it on the new phone.

**Built from:**
| Part | What it does | Tasks |
| :--- | :--- | :--- |
| No internet, no cloud backup | Every build fails if internet access or cloud backup sneaks in; a test proves the app can't connect | T0.3 |
| Inspectable code | The public repository (source available, not open source), license, and README explain how anyone can verify the privacy claims | T0.2, R3 |
| Exclusion rules | Keep chosen folders, date ranges, or people out of memories and widgets | T3.6 |
| Label backup | Export and import names, hidden photos, "Not junk" choices, favorites, rules, and albums | T3.8 |
| Hidden photos | See Feature 1 | T1.9 |

---

## Feature 10: Overnight Photo Analysis

**What it is:** The work behind search, junk filtering, trips, and people happens automatically while your phone charges, and pauses if the phone gets warm. If you don't want to wait, "Index now" starts it immediately, with a progress notification.

**Example:** You install Memories in the evening. On This Day works right away. You plug in your phone overnight, and in the morning search can find most of your photos. On a trip with a new batch of photos, you tap "Index now" in Settings and can search them within minutes.

**Built from:**
| Part | What it does | Tasks |
| :--- | :--- | :--- |
| Analysis pipeline | Analyzes photos in batches while charging and idle; most important photos first; resumes after interruptions; pauses when hot | T2.6 |
| Index now | Starts analysis on demand with a visible progress notification and a Stop button | T2.6 |
| Progress and "all set" | A "Getting to know your photos" card showing what's ready and what's coming, hints on Trips and People while they wait, and one "Your photos are ready" notification when analysis first finishes | T2.7, T3.5, T3.7 |
| Background check | Spots when the phone is stopping reminders or analysis from running while the app is closed, explains why, and shows phone-specific steps to fix it | T1.16, T2.6 |
| Low-memory phones | Lighter settings on phones with less than 4 GB of RAM, with face grouping as an opt-in | T5.1 |

---

## Feature 11: Accessible for Everyone

**What it is:** Works with screen readers, large text, and right-to-left languages, and is ready for translation.

**Example:** A user who relies on TalkBack hears each photo described by its date ("Photo, March 10, 2024"), and can move through a story with "Next" and "Pause" actions.

**Built from:**
| Part | What it does | Tasks |
| :--- | :--- | :--- |
| Accessibility | Screen reader labels, touch targets, contrast, and large text checks | T5.3 |
| Translation readiness | Right-to-left layouts, long-text checks, and support for added languages (search stays English) | T5.4 |

---

## Feature 12: Memory Reminders

**What it is:** Gentle notifications, at most two a day at random daytime moments, that bring you back to your photos. One points to a real memory, worded to fit it: a trip, a day outdoors, a get-together, a meal, your pet, a past year, or last month. The other nudges you to take a photo today so you'll have a memory next year. Wordings vary so they don't feel repetitive. Everything is created on the phone, and you can turn either kind off.

**Example:** Mid-morning, your phone shows "That time in Lisbon: 2 years ago this week" with a photo from the trip. You tap it and the trip story plays. That evening, a second notification asks "What was your highlight today?" You tap it, the camera opens, and you snap dinner with friends. Next week, a different memory arrives as "A moment with nature."

**Built from:**
| Part | What it does | Tasks |
| :--- | :--- | :--- |
| Reminders, date lines, and photo nudges | Picks two random times between 9 AM and 9 PM; "On this day" and "This time last month" lines; varied photo nudges that fit the time of day; no line repeats within a week; hides photos (and places and people) on the lock screen; two switches in Settings | T1.13 |
| Theme lines | "A moment with nature," "A get-together to remember," "Remember this meal?" and "A furry throwback," chosen from what the photos show | T2.9 |
| Place lines | "That time in Lisbon" and "Back in Lisbon" for trip anniversaries | T3.9 |

---

## Feature 13: Albums

**What it is:** An Albums tab with three kinds of albums. Your own albums hold any photos you choose and stay until you delete them. Collections are sorted for you: Favorites, Screenshots, Chat screenshots, Receipts & documents, Blurry, and Rediscover (a fresh set of 50 forgotten photos every 2 days). Suggested albums come from your trips, people, and pets: one new album every 2 days, up to 5 at a time, and you can pin any one you want to keep. Removing an album never deletes a photo.

**Example:** You make an album called "Wedding" and add 40 photos from the grid in one go. A week later the Albums tab suggests "Trip to Lisbon, 2024" with its 50 best photos, so you pin it. You open Receipts & documents, tap Select all, and move last year's receipts to the trash. You never look at Blurry, so you remove it from the tab.

**Built from:**
| Part | What it does | Tasks |
| :--- | :--- | :--- |
| Your albums | Create, rename, pick a cover, add and remove photos, and delete albums; Favorites and Screenshots collections; remove and restore collections | T1.15 |
| Select many photos | Select several photos or a whole album at once, then add them to an album, share, hide, or trash them | T1.14 |
| Sorted collections | Finds chat screenshots, receipts and documents, and blurry photos for their own collections | T2.10 |
| Suggested albums | Builds albums from trips, people, and pets with 30 or more photos, adds one every 2 days, keeps 5, and lets you pin or remove them | T3.10 |
| Rediscover | A new set of 50 photos that aren't in any trip, person, or pet album, every 2 days, with no repeats until all have been shown | T3.11 |
| Album backup | Your albums, removed collections, and past suggestions are included in the label backup | T3.8 |

---

## Foundations (no single feature)

These tasks support every feature: setup, tooling, models, quality checks, and release.

| Part | What it does | Tasks |
| :--- | :--- | :--- |
| Project setup | Repository, workstation, project structure, and automated checks | T0.1, T0.2 |
| AI models | License review, model conversion, and on-phone speed testing | T0.4, T0.5, T0.6 |
| Shared rules and storage | Date rules, eligibility rules, and the on-phone database | T1.1, T1.3 |
| Speed and stability | Startup and scrolling speed, leak checks, and log privacy checks | T5.2 |
| Final checks | Final accuracy, battery, and home-screen-app testing | T5.5 |
| Release | Play Console, signing, privacy policy and listing, closed test, store forms, and rollout | R1, R2, R3, R4, R5, R6 |

---

## Not in v1

Video analysis, NAS network storage, non-English search, telling individual pets apart, a built-in photo editor, cloud backup or sync, an iPhone app, and monetization. See PRD section 7.
