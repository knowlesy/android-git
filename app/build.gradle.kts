import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import java.util.zip.ZipEntry

plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.compose.compiler)
  alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.example.gitsync"
    compileSdk = 36
    defaultConfig {
        applicationId = "com.example.gitsync"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
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

kotlin {
    jvmToolchain(17)
}

dependencies {
  val composeBom = platform(libs.androidx.compose.bom)
  implementation(composeBom)
  androidTestImplementation(composeBom)

  // Core Android dependencies
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.security.crypto)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.activity.compose)

  // Arch Components
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.viewmodel.compose)

  // Compose
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.compose.material3)
  // Tooling
  debugImplementation(libs.androidx.compose.ui.tooling)
  // Instrumented tests
  androidTestImplementation(libs.androidx.compose.ui.test.junit4)
  debugImplementation(libs.androidx.compose.ui.test.manifest)

  // Local tests: jUnit, coroutines, Android runner
  testImplementation(libs.junit)
  testImplementation(libs.kotlinx.coroutines.test)

  // Instrumented tests: jUnit rules and runners
  androidTestImplementation(libs.androidx.test.core)
  androidTestImplementation(libs.androidx.test.ext.junit)
  androidTestImplementation(libs.androidx.test.runner)
  androidTestImplementation(libs.androidx.test.espresso.core)

  // Navigation
  implementation(libs.androidx.navigation3.ui)
  implementation(libs.androidx.navigation3.runtime)
  implementation(libs.androidx.lifecycle.viewmodel.navigation3)

  // JGit patched local JAR
  implementation(files(layout.buildDirectory.file("libs/org.eclipse.jgit-patched.jar")))
  implementation("com.googlecode.javaewah:JavaEWAH:1.2.3")
  implementation("org.slf4j:slf4j-api:2.0.9")

  // Material Icons Extended for a richer UI
  implementation("androidx.compose.material:material-icons-extended")
}

// Create a separate configuration to resolve the remote JGit JAR
val jgitRemote by configurations.creating

dependencies {
  jgitRemote("org.eclipse.jgit:org.eclipse.jgit:7.6.0.202603022253-r")
}

val patchJGit = tasks.register("patchJGit") {
    val patchedJarFile = layout.buildDirectory.file("libs/org.eclipse.jgit-patched.jar")
    inputs.files(jgitRemote)
    outputs.file(patchedJarFile)
    
    doLast {
        val jgitJar = inputs.files.firstOrNull { it.name.startsWith("org.eclipse.jgit-") }
        if (jgitJar != null) {
            val outJar = patchedJarFile.get().asFile
            outJar.parentFile.mkdirs()
            
            ZipInputStream(jgitJar.inputStream()).use { zipIn ->
                ZipOutputStream(outJar.outputStream()).use { zipOut ->
                    var entry = zipIn.nextEntry
                    while (entry != null) {
                        val name = entry.name
                        if (name != "org/eclipse/jgit/lib/InflaterCache.class" && 
                            name != "org/eclipse/jgit/lib/InflaterCache\$SafeInflater.class") {
                            zipOut.putNextEntry(ZipEntry(name))
                            zipIn.copyTo(zipOut)
                            zipOut.closeEntry()
                        } else {
                            println("Excluding $name from patched JGit JAR")
                        }
                        entry = zipIn.nextEntry
                    }
                }
            }
            println("Successfully patched JGit JAR by filtering out InflaterCache classes")
        } else {
            throw GradleException("Could not find org.eclipse.jgit JAR in inputs")
        }
    }
}

// Hook our task into the compilation chain
tasks.matching { it.name.startsWith("compile") || it.name.startsWith("preBuild") }.all {
    dependsOn(patchJGit)
}

