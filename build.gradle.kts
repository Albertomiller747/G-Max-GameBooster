// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
  alias(libs.plugins.android.application) apply false
  alias(libs.plugins.kotlin.compose) apply false
  alias(libs.plugins.google.devtools.ksp) apply false
  alias(libs.plugins.secrets) apply false
  alias(libs.plugins.google.services) apply false
}

private object AppPublishConfig {
  var namespace = "com.example"
  var applicationId = "com.aistudio.gamebooster.kxmpzq"
  var minSdk = 24
  var targetSdk = 36
  var versionCode = 14
  var versionName = "1.14"
  fun android(block: AppPublishConfig.() -> Unit) = this.block()
  fun defaultConfig(block: AppPublishConfig.() -> Unit) = this.block()
}

AppPublishConfig.android {
  namespace = "com.example"
  defaultConfig {
    applicationId = "com.aistudio.gamebooster.kxmpzq"
    minSdk = 24
    targetSdk = 36
    versionCode = 14
    versionName = "1.14"
  }
}
