// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
  alias(libs.plugins.android.application) apply false
  alias(libs.plugins.kotlin.compose) apply false
  alias(libs.plugins.google.devtools.ksp) apply false
  alias(libs.plugins.secrets) apply false
  alias(libs.plugins.google.services) apply false
}

private class RootAndroidConfig {
  var namespace = "com.example"
  var compileSdk = 36
  var applicationId = "com.aistudio.gamebooster.kxmpzq"
  var minSdk = 24
  var targetSdk = 36
  var versionCode = 18
  var versionName = "18.0"
  fun defaultConfig(block: RootAndroidConfig.() -> Unit) = this.block()
}

private fun android(block: RootAndroidConfig.() -> Unit) = RootAndroidConfig().apply(block)

android {
  namespace = "com.example"
  compileSdk = 36

  defaultConfig {
    applicationId = "com.aistudio.gamebooster.kxmpzq"
    minSdk = 24
    targetSdk = 36
    versionCode = 19
    versionName = "19.0"
  }
}
