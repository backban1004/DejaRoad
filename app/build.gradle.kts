import java.util.Properties

plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.compose.compiler)
}

// release 서명 정보는 저장소에 올리지 않는 keystore.properties(프로젝트 루트)에서 읽는다.
// 파일이 없으면 서명 없이 release를 빌드한다 (keystore.properties.example 참고).
val keystoreProperties =
  Properties().apply {
    val file = rootProject.file("keystore.properties")
    if (file.exists()) file.inputStream().use { load(it) }
  }

android {
    namespace = "com.dejaroad.app"
    compileSdk = 36
    // 차량 속도 등 Android Automotive 표준 Car API
    useLibrary("android.car")
    defaultConfig {
        applicationId = "com.dejaroad.app"
        minSdk = 28
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
    }

    signingConfigs {
        if (!keystoreProperties.isEmpty) {
            create("release") {
                storeFile = file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
                // Pleos는 서명 키 교체(키 리니지)를 위해 minSdk 28 이상을 요구한다. 키 리니지는 APK 서명 v3에 담긴다
                enableV2Signing = true
                enableV3Signing = true
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.findByName("release")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
      compose = true
      aidl = false
      buildConfig = false
      shaders = false
    }

    packaging {
      resources {
        excludes += "/META-INF/{AL2.0,LGPL2.1}"
      }
    }
}

// 공모전 제출 규칙에 맞춰 release APK를 apk/{팀명}_DejaRoad_release.apk로 복사한다.
// 사용: gradlew :app:exportReleaseApk -PteamName=팀명
tasks.register<Copy>("exportReleaseApk") {
  // 구성 캐시와 호환되도록 파일 이름을 문자열로 먼저 정해 두고 rename에서는 그 값만 쓴다
  val apkName = providers.gradleProperty("teamName").getOrElse("TEAM") + "_DejaRoad_release.apk"
  dependsOn("assembleRelease")
  from(layout.buildDirectory.dir("outputs/apk/release")) { include("*-release.apk") }
  into(rootProject.layout.projectDirectory.dir("apk"))
  rename { apkName }
}

dependencies {
  // Pleos Connect SDK
  implementation(libs.pleos.vehicle)
  implementation(libs.pleos.navihelper)
  implementation(libs.pleos.tts)
  implementation(libs.pleos.stt)
  implementation(libs.pleos.fusedlocation)

  val composeBom = platform(libs.androidx.compose.bom)
  implementation(composeBom)

  // Core Android dependencies
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.activity.compose)

  // Compose
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.compose.material3)
  debugImplementation(libs.androidx.compose.ui.tooling)

  testImplementation(libs.junit)
  // 단위 테스트에서 assets JSON을 읽기 위한 실제 org.json (Android 스텁 대신)
  testImplementation(libs.org.json)
}
