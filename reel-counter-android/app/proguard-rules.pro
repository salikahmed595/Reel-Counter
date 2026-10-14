# Room, Glance, WorkManager, Billing and DataStore ship their own consumer rules.
-keep class com.reelcounter.app.tracking.service.ReelAccessibilityService

# Strip verbose/debug logging from release builds.
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
}
