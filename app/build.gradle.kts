// Gradle build config

plugins {
  id("com.android.application")
  id("org.jetbrains.kotlin.android")
  id("org.jetbrains.kotlin.plugin.compose")
  id("com.google.devtools.ksp")
  alias(libs.plugins.roborazzi)
}

// 本地调试密钥库：不在仓库提交，首次构建自动生成（保证 clone 后可直接编译）
val debugKeystoreFile = file("${rootDir}/debug.keystore")
if (!debugKeystoreFile.exists()) {
  val javaHome = System.getProperty("java.home")
  val keytoolName = if (System.getProperty("os.name").lowercase().contains("win")) "keytool.exe" else "keytool"
  val keytool = File(javaHome, "bin/$keytoolName")
  val pb = ProcessBuilder(
    keytool.absolutePath,
    "-genkeypair", "-v",
    "-keystore", debugKeystoreFile.absolutePath,
    "-alias", "androiddebugkey",
    "-keyalg", "RSA", "-keysize", "2048", "-validity", "10000",
    "-storepass", "android", "-keypass", "android",
    "-dname", "CN=Android Debug,O=Android,C=US"
  )
  pb.redirectErrorStream(true)
  pb.start().waitFor()
}

android {
  namespace = "com.example"
  compileSdk = 35

  defaultConfig {
    applicationId = "com.aistudio.novelreader.kxmpzq"
    minSdk = 24
    targetSdk = 35
    versionCode = 195
    versionName = "1.0.5"

    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

    // 极致瘦身：当前目标设备均为 arm64（华为/主流手机）；保留其它 ABI 会多出约 16MB 原生库
    ndk {
      // release 仅 arm64（瘦身）；debug 额外带 x86_64——模拟器原生执行 ONNX，
      // 走 ARM 翻译层跑 onnxruntime 会 SIGSEGV（第十五轮实测）
      abiFilters += if (gradle.startParameter.taskNames.any { it.contains("Release") } &&
        !project.hasProperty("includeX86")
      ) {
        listOf("arm64-v8a")
      } else {
        listOf("arm64-v8a", "x86_64")
      }
    }

    // 仅保留中英文资源，去掉无用的 locale 资源
    resConfigs("zh-rCN", "en")
  }

  signingConfigs {
    create("release") {
      val keystorePath = System.getenv("KEYSTORE_PATH") ?: "${rootDir}/my-upload-key.jks"
      storeFile = file(keystorePath)
      storePassword = System.getenv("STORE_PASSWORD")
      keyAlias = "upload"
      keyPassword = System.getenv("KEY_PASSWORD")
    }
    create("debugConfig") {
      storeFile = file("${rootDir}/debug.keystore")
      storePassword = "android"
      keyAlias = "androiddebugkey"
      keyPassword = "android"
    }
  }

  buildTypes {
    release {
      isCrunchPngs = true
      isMinifyEnabled = true
      isShrinkResources = true
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
      val keystoreFile = file("${rootDir}/my-upload-key.jks")
      signingConfig = if (keystoreFile.exists() && System.getenv("STORE_PASSWORD") != null) {
        signingConfigs.getByName("release")
      } else {
        signingConfigs.getByName("debugConfig")
      }
    }
    debug { signingConfig = signingConfigs.getByName("debugConfig") }
  }

  bundle {
    density {
      enableSplit = true
    }
    abi {
      enableSplit = true
    }
    language {
      enableSplit = true
    }
  }
  packaging {
    jniLibs {
      // 压缩原生库（cronet/quickjs），APK 更小；安装时再解压
      useLegacyPackaging = true
    }
  }
  packagingOptions {
    jniLibs.useLegacyPackaging = true
  }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
  }
  kotlinOptions {
    jvmTarget = "11"
  }
  buildFeatures {
    compose = true
    buildConfig = true
  }
  testOptions { unitTests { isIncludeAndroidResources = true } }
}

dependencies {
  implementation(platform(libs.androidx.compose.bom))
  implementation(libs.androidx.activity.compose)
  implementation(libs.androidx.compose.material.icons.core)
  implementation(libs.androidx.compose.material.icons.extended)
  implementation(libs.androidx.compose.material3)
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.graphics)
  implementation(project(":liquidglass-compose"))
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.security.crypto)
  implementation(libs.androidx.work.runtime.ktx)
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.lifecycle.viewmodel.compose)
  implementation(libs.androidx.navigation.compose)
  implementation("dev.chrisbanes.haze:haze:1.1.1")
    implementation(project(":backdrop"))
  // 第十一轮瘦身：flexible-bottomsheet-material3 引入后从未使用（全项目零 import），移除
  implementation("org.brotli:dec:0.1.2")
  implementation(libs.androidx.room.ktx)
  implementation(libs.androidx.room.runtime)
  implementation(libs.coil.compose)
  implementation(libs.jsoup)
  implementation(libs.kotlinx.coroutines.android)
  implementation(libs.kotlinx.coroutines.core)
  // 第十一轮瘦身：moshi-kotlin（连带 kotlin-reflect）已移除——仅有的两个序列化点
  //（BackupManager / RemoteEndpointProvider）改为 org.json 手写，JSON 兼容不变
  implementation(libs.okhttp)
  // 漫画翻译（第十五轮）：ONNX Runtime 跑 PP-OCR 检测/识别（纯 CPU，arm64）
  implementation("com.microsoft.onnxruntime:onnxruntime-android:1.28.0")
  // 第十七轮：ML Kit 移除（国内设备普遍无谷歌服务）——在线兜底改腾讯交互翻译（国内直连免费）
  implementation(libs.quickjs.kt)
  implementation(libs.cronet.api)
  implementation(libs.cronet.embedded)
  testImplementation(libs.androidx.compose.ui.test.junit4)
  testImplementation(libs.androidx.core)
  testImplementation(libs.androidx.junit)
  testImplementation(libs.junit)
  testImplementation(libs.kotlinx.coroutines.test)
  testImplementation(libs.robolectric)
  testImplementation(libs.roborazzi)
  testImplementation(libs.roborazzi.compose)
  testImplementation(libs.roborazzi.junit.rule)
  androidTestImplementation(platform(libs.androidx.compose.bom))
  androidTestImplementation(libs.androidx.compose.ui.test.junit4)
  androidTestImplementation(libs.androidx.espresso.core)
  androidTestImplementation(libs.androidx.junit)
  androidTestImplementation(libs.androidx.runner)
  debugImplementation(libs.androidx.compose.ui.test.manifest)
  debugImplementation(libs.androidx.compose.ui.tooling)
  "ksp"(libs.androidx.room.compiler)
}


