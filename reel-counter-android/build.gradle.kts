plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.ksp) apply false
}

// OneDrive sync locks files mid-build; keep outputs outside synced folders when needed.
val localAppData: String? = System.getenv("LOCALAPPDATA")
if (rootDir.absolutePath.contains("OneDrive") && localAppData != null) {
    allprojects {
        layout.buildDirectory.set(file("$localAppData/ReelCounterBuild/${project.name}"))
    }
}
