# إلى اليوم (Ela Al-Yawm)

Offline-first personal Android prayer tracker for the period 15 July 2026 through 15 July 2027.

## Open and run

1. Open this folder in Android Studio Ladybug or newer.
2. Let Gradle sync, then run the `app` configuration on a physical Android device.
3. Grant location, notification, and exact-alarm permissions when prompted. Location is used solely to calculate prayer times and is saved locally with the day’s prayer-time record.

## Important configuration

The project uses the current signed-in Google account to access only the application’s private Drive `appDataFolder`. Configure an Android OAuth client for the app package and signing certificate in Google Cloud before using backup/restore. The app otherwise works entirely offline.

Tajawal is declared as an optional bundled resource. Put `Tajawal-Regular.ttf` and `Tajawal-Bold.ttf` in `app/src/main/res/font/` to enable it; the app deliberately falls back to Android's Arabic sans-serif font until those files are supplied. This avoids distributing an unlicensed or corrupted binary font in source control.

## Notes

- The app limits all writes to the requested tracking window and becomes read-only after 15 July 2027.
- Export targets the Android Downloads collection, not legacy external-storage paths.
- The build uses Compose Material 3, Room, Adhan, Glance, WorkManager, Apache POI, Google Sign-In and the Drive REST client.

