# Development Guide

This guide covers project setup, local testing, and the build flow for the Mobile Lyrics Presenter for Choirs project.

## Dark Spatial UI

The Figma source of truth is [Mobile Lyrics Presenter — Dark Spatial](https://www.figma.com/design/huTRIbqg23kIuOI8ucj3tJ). It contains the Dark Spatial token board, component variants, nine phone flows, four TV states, matching error-state artboards, and the Connect → Library → Live → error → recovery prototype. UI tokens map to `controller/app/src/main/java/com/lyricsapp/controller/DarkSpatialTheme.kt` and `receiver/app/src/main/java/com/lyricsapp/receiver/DarkSpatialTheme.kt`. See `DARK_SPATIAL_DESIGN.md` for the token mapping and implementation notes.

## Project structure

```text
d:\slideshow
├── controller/
│   └── app/
│       ├── src/main/java/com/lyricsapp/controller/
│       ├── src/main/AndroidManifest.xml
│       └── build.gradle.kts
├── receiver/
│   └── app/
│       ├── src/main/java/com/lyricsapp/receiver/
│       ├── src/main/AndroidManifest.xml
│       └── build.gradle.kts
├── shared/
│   └── src/main/java/com/lyricsapp/shared/
├── PROTOCOL.md
├── PROJECT_PLAN.md
├── DEVELOPMENT.md
├── QUICK_REFERENCE.md
├── README.md
├── build.gradle.kts
├── settings.gradle.kts
├── .gitignore
└── LICENSE
```

## Local environment requirements

- Android Studio 2023.1 or newer
- Android SDK with API 30+ enabled
- JDK 11 or newer
- Gradle 8.1 or newer
- A phone and an Android TV, or Android emulator variants for both

## First-time setup

### Open the project in Android Studio

1. Launch Android Studio
2. Choose File > Open
3. Select the repository root: `d:\slideshow`
4. Allow Gradle sync to finish
5. If prompted, install missing Android SDK components

### Validate the Gradle project

From the command line:

```bash
cd d:\slideshow
./gradlew tasks
```

If sync works, you are ready to build and run the apps.

## Build commands

### Build all modules

```bash
cd d:\slideshow
./gradlew build
```

### Build only the TV receiver app

```bash
cd d:\slideshow
./gradlew :receiver:app:build
```

### Build only the phone controller app

```bash
cd d:\slideshow
./gradlew :controller:app:build
```

### Install on connected devices

```bash
cd d:\slideshow
./gradlew installDebug
```

### Install specific app

```bash
cd d:\slideshow
./gradlew :receiver:app:installDebug
./gradlew :controller:app:installDebug
```

## Device setup

### Android TV / TV emulator

Use either:

- a real Android TV with WiFi connected
- an Android TV emulator from Android Studio AVD Manager

### Phone / emulator

Use either:

- a physical Android phone on the same WiFi network
- a phone emulator in Android Studio

## Recommended testing setup

For the best validation you should run:

- controller app on a phone or phone emulator
- receiver app on a TV or TV emulator
- both devices on the same local WiFi network

## Basic connection test

1. Run the receiver app on the TV device
2. Note the TV's local IP address from network settings
3. Run the controller app on the phone
4. Enter the receiver host or IP in the controller
5. Tap Connect
6. Tap Next or Previous to confirm messages reach the TV

## Discovery test

1. Keep the receiver app running on the TV
2. Start the controller app on the phone
3. Wait for the discovery service to find the receiver
4. Choose the TV from the discovered list
5. Confirm the connection opens without manual IP input

## Reconnect test

1. Connect the controller to the receiver
2. Turn off the TV or drop WiFi on the receiver side
3. Wait for the controller to mark the connection as disconnected
4. Restore connectivity
5. Confirm the app reconnects and resumes from the latest display state

## Heartbeat validation

The controller should send a heartbeat at a fixed interval, and the receiver should answer with a heartbeat acknowledgement.

Check the logs with:

```bash
adb logcat -s "WebSocketClient" "WebSocketServer" "DiscoveryManager"
```

Expected behavior:

- no stale connection remains unnoticed
- disconnects are detected within a short timeout window
- reconnect attempts happen with backoff instead of a flood of retries

## Debugging checklist

### If the controller cannot connect
- confirm both devices are on the same WiFi or hotspot
- verify the receiver host address is correct
- check the receiver app is running
- ensure port 8765 is available and not blocked by a firewall

### If the receiver does not show any updates
- confirm the controller sent a valid JSON message
- inspect logs for parse or socket errors
- verify the receiver is still in the connected state

### If discovery does not work
- confirm both devices are on the same network segment
- ensure the service is actually being advertised on the receiver side
- try manual connection as a fallback while debugging the discovery layer

## Suggested local validation sequence

1. Start receiver app on TV
2. Start controller app on phone
3. Verify controller status changes to connected
4. Send `NEXT_SLIDE` and confirm receiver updates
5. Send `PREV_SLIDE` and confirm the prior slide appears
6. Trigger `BLACK_SCREEN` and then `CLEAR_SCREEN`
7. Disable WiFi briefly and confirm reconnect logic
8. Re-run long session test for 10–20 minutes

## Android Studio workflow

### Debugging in Android Studio

- open the Logcat tab
- use filters for `WebSocketClient`, `WebSocketServer`, and `DiscoveryManager`
- run the receiver app on the TV emulator or device
- run the controller app on the phone emulator or device

### Emulator recommendations

For reliability, use different emulator targets:

- Android TV emulator for the receiver
- ordinary phone emulator for the controller

This ensures the app is tested against the real device form factors it targets.

## Testing by phase

### Phase 1 checks
- receiver starts and shows ready state
- controller can connect and send navigation messages
- receiver updates display based on message type
- reconnect logic triggers after network drop
- service discovery finds the receiver

### Phase 2 checks
- songs can be created, edited, and saved locally
- slides can be split and reordered
- setlist order persists and can be restored

### Phase 3 checks
- live control remains steady during a quick service flow
- next/previous controls respond fast
- jump-to-slide works correctly

### Phase 4 checks
- readability on a large TV screen
- theme controls persist correctly
- clear and black transitions feel intentional

## Troubleshooting matrix

| Problem | Likely cause | Suggested fix |
| --- | --- | --- |
| No connection | Same network not available | Put both devices on same WiFi or hotspot |
| Receiver not discovered | NSD not started or wrong service type | Check discovery configuration and logs |
| Messages not displayed | WebSocket not open | Verify connection and check logs |
| Reconnect fails | Timeout or stale socket | Reset socket and retry with exponential backoff |
| UI not updating | stale state on client | resend latest display state after reconnect |

## Media transfer and playback

The receiver owns the media endpoint on TCP port 8766, advertised as the
`mediaPort` NSD attribute and repeated in the WebSocket handshake ACK. The
controller streams Photo Picker input with HTTP chunked transfer encoding to
`POST /media`; the receiver writes directly into its cache instead of routing
large media bodies through the WebSocket or keeping the whole upload in RAM.
This choice keeps WebSocket heartbeats responsive and gives Media3 a stable
receiver-local URL with HTTP byte-range seeking. The cache is app-local and can
be reclaimed by Android; re-upload media if its ID is no longer available.
Uploads are limited to 2 GiB. The HTTP endpoint is cleartext but only intended
for the same trusted local network, like the existing WebSocket endpoint.

Images use Coil with a crossfade and target-size decoding. Videos use Media3
with looping and mute enabled by default; the player is released when its
screen lifecycle stops or disposes. Fill, fit, and stretch map to crop, fit,
and fill-bounds rendering. Unsupported codecs and storage failures return an
HTTP error and a `MEDIA_ERROR` event where the WebSocket is still connected.
The controller's Media tab keeps the imported-media grid and picker thumbnail
references in DataStore, using persistable document-picker access.

The receiver hides system bars and requests the highest supported display mode
at or above 3840x2160 when available; devices without that mode keep their
native resolution and still render edge-to-edge. The song creator uses a 16:9
preview scaled against the receiver's logical display width. The live template
picker is collapsed by default and expands inline when its header is selected.
The controller turns ACKs into short status toasts rather than rendering raw
protocol JSON in the app.

For cross-device media playback, the controller builds the media URL from the
same resolved receiver host used by its WebSocket connection. The receiver
accepts regular and suffix byte ranges (`bytes=N-M`, `bytes=N-`, and
`bytes=-N`) for seeking. The media endpoint remains on the receiver, so phone
local `content://` URIs are never sent to the TV.

## Protocol v2 and templates

See [PROTOCOL.md](PROTOCOL.md) for the optional handshake, layer, template, and
media messages. Older protocol fields and command names are unchanged. Built-in
templates are immutable; custom templates are stored as JSON in controller
DataStore. Template payloads travel in full with `APPLY_TEMPLATE`, so the TV
does not need the controller's template store.

## Log commands

```bash
adb logcat
adb logcat -s "WebSocketClient" "WebSocketServer" "DiscoveryManager"
adb devices
```

## Useful developer reminders

- Keep the protocol simple and backward-compatible
- Preserve local-first behavior
- Prefer network reliability over fancy features early in development
- Validate on actual Android TV hardware before broad feature expansion
- Make display readability a top priority; it matters more than aesthetics in a live church setting

## Recommended implementation order

1. Finish the local WebSocket contract
2. Verify receiver and controller connect reliably
3. Add discovery and recovery flow
4. Add local persistence and song editing
5. Add live control and setlist features
6. Add display polish and theme settings
7. Test on real Android TV devices and hotspot mode

## Summary

The project is intentionally being built in tight phases. The early phase focuses on proving that a phone can reliably control an Android TV screen over a local network. Once that is stable, all downstream features such as song editing, setlists, and display themes can be built on top of a proven connection layer.

## Presentation connection reliability

The controller's `ConnectionKeepAliveService` owns the OkHttp WebSocket,
heartbeat and reconnect jobs; Activities bind to it for state updates and must
not close the socket when they stop or rotate. The service runs in the
foreground with a low-importance notification and a bounded partial wake lock.
The last host, display payload, active background, and visibility override are
persisted so a service/process restart can reconnect and restore the same
presentation after the receiver's blank handshake. Reconnect waits use 1, 2,
4, 8, then 16 seconds; two missed heartbeat acknowledgements force a reconnect.

Battery optimization restrictions vary by manufacturer. The app offers a
shortcut to Android's battery optimization settings after repeated connection
drops. On some Chinese OEM Android ROMs, also enable the app's **Allow
background activity** / auto-start setting in the manufacturer's app settings.
These controls are outside Android's standard battery settings and names vary
by device.

Receiver output is independent background, lyrics, and overlay layers. Slide
commands and `CLEAR_SCREEN` do not tear down active media. `BLACK_SCREEN` is a
black cover over retained layer state; use `CLEAR_BACKGROUND` or `CLEAR_ALL` to
remove media. On every socket open, the TV first enters pure-black `BLANK` and
waits for the controller's persisted state restore.
