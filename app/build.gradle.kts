plugins { id("com.android.application") }
android {
    namespace = "app.socialpause"
    compileSdk = 36
    buildToolsVersion = "36.0.0"
    defaultConfig {
        applicationId = "app.socialpause"
        minSdk = 33
        targetSdk = 36
        versionCode = 6
        versionName = "0.4.2"
    }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
}
dependencies { implementation(project(":engine")) }
