# Getting Started - Opening in Android Studio

This guide walks you through opening and running the Lyrics Presenter project in Android Studio.

## Prerequisites

1. **Android Studio** 2023.1 or later
   - Download from: https://developer.android.com/studio
   - Install and launch

2. **Android SDK**
   - API 30 or higher
   - Should be auto-installed with Android Studio
   - Go to Settings > SDK Manager to verify

3. **Java/JDK 11+**
   - Usually comes with Android Studio
   - If not: Install from adoptopenjdk.com

4. **Git** (optional but recommended)
   - For version control

---

## Opening the Project

### Option A: Open Root Project (Recommended)

This way you can build/run both apps from one window.

1. Launch **Android Studio**
2. Click **File > Open**
3. Navigate to: `d:\slideshow`
4. Click **OK**
5. Wait for Gradle sync to complete (first time: ~5 minutes)

You should see:
- Left sidebar with: `receiver`, `controller`, `shared`
- Console output showing "Build Successful"

### Option B: Open Individual Apps

If you want separate windows for each app:

**For Receiver (TV App):**
1. **File > Open**
2. Navigate to: `d:\slideshow\receiver`
3. **OK**

**For Controller (Phone App):**
1. **File > Open**
2. Navigate to: `d:\slideshow\controller`
3. **OK**

---

## Building the Apps

### Build Everything
```
Build > Make Project
(or Ctrl+F9)
```

### Build Specific App
1. Bottom-left, select "Build Variants"
2. For `receiver:app`, choose "Debug"
3. For `controller:app`, choose "Debug"
4. Build > Make Project

---

## Running on Emulator

### Step 1: Create AVDs (Android Virtual Devices)

**For TV (Receiver):**
1. **Tools > Device Manager**
2. **Create Virtual Device**
3. Select **Category: TV**, then choose "Android TV (720p)" or similar
4. Choose API 30 or higher
5. Name it: `tv_emulator` or similar
6. **Finish**

**For Phone (Controller):**
1. **Tools > Device Manager**
2. **Create Virtual Device**
3. Select **Category: Phone**, then choose "Pixel 6" or any model
4. Choose API 26 or higher
5. Name it: `phone_emulator` or similar
6. **Finish**

### Step 2: Launch Emulators

1. **Tools > Device Manager**
2. Find your emulators
3. Click the **▶ (Play)** icon to launch each
4. Wait for them to fully boot (emulator shows home screen)

### Step 3: Deploy Apps

**Deploy Receiver:**
1. Click on `receiver` module (left sidebar)
2. **Run > Run 'receiver'** (or Shift+F10)
3. Select the **TV emulator**
4. App should launch on TV emulator
5. You'll see "Waiting for connection..."

**Deploy Controller:**
1. Click on `controller` module (left sidebar)
2. **Run > Run 'controller'** (or Shift+F10)
3. Select the **Phone emulator**
4. App should launch on phone
5. You'll see IP input field

---

## Running on Real Devices

### Prerequisites
- Both devices on **same WiFi network**
- USB cable (for computer ↔ device connection)
- USB debugging enabled on both devices

### Enable USB Debugging

**On TV:**
1. Settings > About > Build number (tap 7 times until "Developer" appears)
2. Back > System > Developer Options
3. Enable "USB debugging"
4. Plug USB into computer

**On Phone:**
1. Settings > About > Build number (tap 7 times)
2. Back > System > Developer options
3. Enable "USB debugging"
4. Plug USB into computer

### Deploy to Real Devices

**Check connection:**
```bash
adb devices
# Should list both devices
```

**Run Receiver:**
1. Click `receiver` module
2. **Run > Run 'receiver'**
3. Select your TV device
4. App deploys (takes ~30 seconds)

**Run Controller:**
1. Click `controller` module
2. **Run > Run 'controller'**
3. Select your phone device
4. App deploys

---

## Testing the Connection

### After Both Apps Are Running:

1. **Get Receiver's IP**
   - TV: Settings > Network > WiFi > Connected network → IP address
   - (e.g., 192.168.1.100)

2. **In Controller App**
   - Text field shows example IP
   - Clear it and enter **receiver's actual IP**
   - Tap **Connect**

3. **Verify Connection**
   - Controller should show "Connected!" in green
   - TV should show "Connected! Waiting for slides..."

4. **Test Control**
   - In Controller: Tap **Next** button
   - On TV: Should display the message
   - In Controller: Tap **← Prev** button
   - On TV: Should show a different message

---

## Troubleshooting

### Issue: "Gradle Sync Failed"
**Solution:**
- File > Sync Now
- If still fails: File > Invalidate Caches > Invalidate and Restart
- Close and reopen project

### Issue: "Device Not Detected"
**Solution:**
- Check USB cable (try different port)
- Restart adb: `adb kill-server` then `adb start-server`
- Ensure USB debugging is enabled
- Check Windows Device Manager for "Android Device"

### Issue: Apps Install But Won't Run
**Solution:**
- Check **Run > Edit Configurations**
- Module should be `receiver:app` or `controller:app`
- Activity should be `.MainActivity`

### Issue: Connection Refused (Receiver)
**Solution:**
- Verify receiver IP is correct
- Check both devices on same WiFi
- Try turning WiFi off/on
- Check device firewall isn't blocking port 8765

### Issue: "WebSocket Frame Error"
**Solution:**
- This is normal for Phase 1
- Check both apps' Logcat output
- See `PROTOCOL.md` for message format

---

## Viewing Logs

Very helpful for debugging:

```bash
# All logs
adb logcat

# Filter by tag
adb logcat -s "WebSocketServer"
adb logcat -s "WebSocketClient"
adb logcat -s "DiscoveryManager"

# Filter by app
adb logcat | grep "lyricsapp"

# Clear logs
adb logcat -c

# Save to file
adb logcat > logs.txt
```

Or use Android Studio's **Logcat** tab (bottom panel).

---

## Project Structure in Android Studio

After opening, you'll see:

```
project/
├── receiver (red folder icon)
│   └── app
│       └── src
│           └── main
│               ├── java
│               │   └── com.lyricsapp.receiver
│               │       ├── MainActivity.kt
│               │       ├── SimpleWebSocketServer.kt
│               │       └── ...
│               └── AndroidManifest.xml
│
├── controller (blue folder icon)
│   └── app (same structure)
│
└── shared (green folder icon)
    └── src/main/java/com.lyricsapp.shared
        └── Message.kt
```

---

## Data model scaffold

The controller now has an offline Room database containing `SongEntity` and
`SetlistEntity`. Song sections and slides are stored as JSON so the editor can
save the complete structure in one transaction. Use `DatabaseProvider.get(...)`
to access the database and `SongRepository` for the app-facing API.

`shared` contains the protocol models plus the portable `Song`, `SongSection`,
`SongSlide`, `Setlist`, and `splitIntoSlides` models used by both apps.

## Next Steps

1. ✅ Open project in Android Studio
2. ✅ Create and launch emulators (or connect real devices)
3. ✅ Deploy both apps
4. ✅ Test connection manually (see **Testing the Connection** above)
5. ✅ Check logcat for any errors
6. ✅ Read `DEVELOPMENT.md` for full testing procedures
7. → Move to Phase 2: Song Data Model

---

## Helpful Resources

- **Android Studio Docs:** https://developer.android.com/studio/intro
- **Kotlin Guide:** https://kotlinlang.org/docs/
- **WebSocket Protocol:** https://tools.ietf.org/html/rfc6455
- **Jetpack Compose:** https://developer.android.com/jetpack/compose

---

## Questions?

- Check `README.md` for project overview
- See `QUICK_REFERENCE.md` for quick commands
- Review `PROTOCOL.md` for message format
- Look at `DEVELOPMENT.md` for testing procedures

---

**Last Updated:** 2026-09-20  
**Current Status:** Phase 1 - Ready to test
