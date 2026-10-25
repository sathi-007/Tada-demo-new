# Tada App

Android demo: set two map points, optionally nickname one, book the pair, then read every saved booking.

There is one activity. Screen changes stay inside it. The map screen’s action reads **Set A**, then **Set B**, then **Book**.

## Setup

1. Install Android Studio and Android SDK platform 37.
2. Copy the keys below into `local.properties` in the project root (this file is gitignored), or export the same names in the environment.

```properties
sdk.dir=/path/to/android/sdk
MAPS_API_KEY=your-google-maps-sdk-key
AQICN_TOKEN=your-aqicn-token
BIGDATACLOUD_API_KEY=your-bigdatacloud-key
```

| Key | Used for | Where it is read |
| --- | --- | --- |
| `MAPS_API_KEY` | Google Maps SDK for Android | Manifest placeholder filled at build time |
| `AQICN_TOKEN` | [AQICN](https://aqicn.org/json-api/doc/) `feed/geo:{lat};{lng}/` | `BuildConfig.AQICN_TOKEN` |
| `BIGDATACLOUD_API_KEY` | [BigDataCloud reverse geocode](https://www.bigdatacloud.com/docs/api/reverse-geocode-to-city) `data/reverse-geocode` | `BuildConfig.BIGDATACLOUD_API_KEY` |

The project builds with those values empty. The map and the two live APIs need real keys to return data. Do not commit keys.

Optional: `BOOKS_BASE_URL`. Leave it empty to use the in-app books mock. Set it to a real base URL (with or without a trailing slash) to bind Retrofit to `POST /books` and `GET /books` instead. Use cases and screens do not change.

## Run

Open the project in Android Studio and run the `app` configuration, or:

```bash
./gradlew :app:installDebug
```

Unit tests:

```bash
./gradlew :app:testDebugUnitTest
```

The app requests fine location. After permission is granted it waits for a fix, centers the map, and the button reads Set A. A denied permission is not part of this demo; the screen shows an error instead of spinning.

## What the screens do

**Screen 1.** The map sits under the status bar and above the bottom card. A pin marks the center. The top-right `aqi` is the air quality at that center. It is requested when the camera stops, including before Set A and after both points are set, and the previous request is cancelled if the camera moves again. That refresh does not use a full-screen loader.

Set A and Set B use a blocking loader and store that center’s coordinates, reverse-geocode address, and air quality. The address is the two `localityInfo.administrative` entries with the highest `order`, joined in ascending `order`. A failed address or air-quality store stays on the same step and does not write the point. Dragging does not change A or B, does not reverse-geocode, and does not write the points file.

Clear sits under the yellow button. It is enabled when A or B has a value. It deletes the points-file records, returns the button to Set A, and recenters on the current location. It does not delete bookings.

Launch starts empty on Set A. Saved points are not restored onto the screen.

**Screen 2.** Tap a set row. The title is the side and the reverse-geocode name, then that point’s `aqi`. The nickname field allows 0–20 characters. **V** saves it to the points file and returns. Screen 1 then shows the nickname, or the address when the nickname is blank. System back leaves the nickname unchanged.

**Screen 3.** Book sends `POST /books` with `latitude`, `longitude`, `aqi`, and `name` for A and for B. `name` is the reverse-geocode address. The screen shows that name, `aqi`, and a nickname line even when the nickname is blank, then `price`. The mock price is a new integer from 1000 through 10000. The response, plus an id and a timestamp, is appended to the bookings file. **V** opens history. System back clears the on-screen labels and returns the button to Set A. It does not delete points-file records and does not recenter.

**Screen 4.** `GET /books` is called with `year` and `month`. Those parameters do not filter the list. The screen reads the bookings file, newest timestamp first, and shows Total Count, Total Price, and each booking’s A and B names. System back returns to Screen 3.

## Architecture

```
Compose screens  ->  one ViewModel per screen
                 ->  use cases (address, Set A / Set B, nickname, clear, back, book, history, camera target, center air quality)
                 ->  repositories
                 ->  Retrofit (AQICN, BigDataCloud, books), the points JSON file, fused location, Google Maps
```

Hilt is wired at the edges. The books mock implements the Retrofit `BooksApi` and is the object bound when `BOOKS_BASE_URL` is empty. Business rules are in use cases, not in composables or ViewModels.

Points live in `files/points.json` and are written only on Set A, Set B, and nickname save. A write updates that side and leaves the other side in place. Only Clear removes those records. Bookings live in `files/bookings.json` and are not cleared with the points.
