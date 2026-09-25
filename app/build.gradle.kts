plugins { id("com.android.application") }
android {
    namespace = "app.socialpause"
    compileSdk = 36
    buildToolsVersion = "36.0.0"
    defaultConfig {
        applicationId = "app.socialpause"
        minSdk = 33
        targetSdk = 36
        versionCode = 9
        versionName = "0.7.0"
        testInstrumentationRunner = "app.socialpause.RuntimeChecks"
    }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
}
dependencies { implementation(project(":engine")) }
