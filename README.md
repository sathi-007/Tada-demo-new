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
| `MAPS_API_KEY` | Google Maps SDK for Android | Copied into the merged manifest as `com.google.android.geo.API_KEY` at build time |
| `AQICN_TOKEN` | [AQICN](https://aqicn.org/json-api/doc/) `feed/geo:{lat};{lng}/` | `BuildConfig.AQICN_TOKEN` |
| `BIGDATACLOUD_API_KEY` | [BigDataCloud reverse geocode](https://www.bigdatacloud.com/docs/api/reverse-geocode-to-city) `data/reverse-geocode` | `BuildConfig.BIGDATACLOUD_API_KEY` |

The project builds with those values empty. Screen 1 reads `com.google.android.geo.API_KEY` from the merged manifest. An empty value or a leftover `${MAPS_API_KEY}` placeholder does not start Google Maps. The screen shows the line to add to `local.properties`:

```properties
MAPS_API_KEY=your-google-maps-sdk-key
```

Rebuild and reinstall after adding it. The environment variable of the same name overrides `local.properties`. An empty key used to load Maps anyway and fail with `SecurityException: Unknown calling package name 'com.google.android.gms'` from Maps dynamite. Maps is started with the application id `com.example.tada`. The manifest queries `com.google.android.gms` and enables hardware acceleration so that startup uses this package. AQICN and BigDataCloud still need their own keys to return data. Do not commit keys.

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

**Screen 1.** Once the manifest contains a Maps key, a full-width white panel sits under the status bar. The word `aqi` and the value are right-aligned in that panel. The map starts below the panel and fills down to the bottom card. A pin marks the center. The panel shows the air quality at that center. It is requested when the camera stops, including before Set A and after both points are set, and the previous request is cancelled if the camera moves again. That refresh does not use a full-screen loader. Without a key, that value is not requested and the screen shows how to set `MAPS_API_KEY` instead of the map.

The status bar and the navigation or gesture bar are tinted blue. Screen content stays inside those insets. The center marker is the app pin, not the default Google pin.

Set A and Set B use a blocking loader and store that center’s coordinates, address, nickname, and air quality. The address is the two `localityInfo.administrative` entries with the highest `order`, joined in ascending `order`. Before calling reverse geocode, the tap looks for a saved place whose latitude and longitude match through the third decimal, cutting off extra digits instead of rounding. `37.5642, 127.0016` matches `37.5645, 127.0018`. `37.5650` does not. On a match, the row shows that place’s address and nickname and a new air-quality value is fetched. Reverse geocode is not called. On a miss, both are fetched and the place is recorded. A failed address or air-quality store stays on the same step and does not write the point. Dragging does not change A or B, does not reverse-geocode, and does not write the points file or the place cache. The air-quality panel is requested 500ms after the camera stops. Another move cancels that wait and any request already in flight. Set A and Set B do not wait. That refresh does not write the cache.

The yellow control reads Set A, then Set B, then Book. There is no Clear control.

Launch starts empty on Set A. Saved points and saved places are not restored onto the screen. The place cache is still there for the next Set A or Set B.

**Screen 2.** Tap a set row. The side letter is vertically centered across the location name and the `aqi` row. The location name sits to the right of the letter. The `aqi` row starts at the same left edge as the name, and the label and value are two equal-width left-aligned columns. The nickname field allows 0–20 characters. **V** saves it to the points file, updates the newest saved place in that third-decimal bucket, and returns. Screen 1 then shows the nickname, or the address when the nickname is blank. System back leaves the nickname unchanged.

**Screen 3.** Book sends `POST /books` with `latitude`, `longitude`, `aqi`, and `name` for A and for B. `name` is the reverse-geocode address. For each side, the letter spans the location name, `aqi`, and nickname rows. Those rows start at the name’s left edge. `aqi` and its value, and `nickname` and its value, are equal-width left-aligned columns. The nickname line stays when the nickname is blank. Then `price`. The mock price is a new integer from 1000 through 10000. The response, plus an id and a timestamp, is appended to the bookings file. **V** opens history. System back clears the on-screen labels and returns the button to Set A. It does not delete points-file records and does not recenter.

**Screen 4.** `GET /books` is called with `year` and `month`. Those parameters do not filter the list. The screen reads the bookings file, newest timestamp first. Total Count and Total Price sit above a clearly visible divider, then a vertical list of each booking’s A and B names. System back returns to Screen 3.

## Architecture

```
Compose screens  ->  one ViewModel per screen
                 ->  use cases (address, place match, Set A / Set B, nickname, back, book, history, camera target, center air quality)
                 ->  repositories
                 ->  Retrofit (AQICN, BigDataCloud, books), the points and places JSON files, fused location, Google Maps
```

Hilt is wired at the edges. The books mock implements the Retrofit `BooksApi` and is the object bound when `BOOKS_BASE_URL` is empty. Business rules are in use cases, not in composables or ViewModels.

Points live in `files/points.json` and are written only on Set A, Set B, and nickname save. A write updates that side and leaves the other side in place. Saved places live in `files/places.json`. A new place is recorded on a Set A or Set B miss, and a nickname save updates the newest place in that bucket. Back from Screen 3 clears the on-screen A and B and does not delete either file. The air-quality panel refresh does not change the place cache. Bookings live in `files/bookings.json`.
