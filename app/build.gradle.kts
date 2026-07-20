plugins {
    id("com.android.application")
}

android {
    namespace = "com.pixeldrift.wallpaper"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.pixeldrift.wallpaper"
        minSdk = 23
        targetSdk = 36
        versionCode = 3
        versionName = "0.1.2"

        testInstrumentationRunner = "android.test.InstrumentationTestRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        create("profile") {
            initWith(getByName("release"))
            applicationIdSuffix = ".profile"
            versionNameSuffix = "-profile"
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += listOf("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    lint {
        warningsAsErrors = true
        // API 37 is still the Android 17 preview; production targets the latest stable API 36.
        // The audited Gradle/AGP pair is deliberately pinned and checksum-verified in CI.
        disable += setOf("OldTargetApi", "AndroidGradlePluginVersion")
    }
}

dependencies {
    testImplementation("junit:junit:4.13.2")
}

val verifyWallpaperSurfaceContract by tasks.registering {
    group = "verification"
    description = "Rejects SurfaceHolder APIs that Android forbids for live wallpapers."

    val sourceRoot = layout.projectDirectory.dir("src/main/java")
    inputs.dir(sourceRoot)

    doLast {
        val forbiddenCall = Regex("""\.\s*setKeepScreenOn\s*\(""")
        val offenders = sourceRoot.asFileTree
            .matching {
                include("**/*.java")
                include("**/*.kt")
            }
            .filter { forbiddenCall.containsMatchIn(it.readText()) }
            .map { it.relativeTo(projectDir).invariantSeparatorsPath }

        check(offenders.isEmpty()) {
            "WallpaperService's SurfaceHolder always throws from setKeepScreenOn(): " +
                offenders.joinToString()
        }
    }
}

tasks.named("preBuild").configure {
    dependsOn(verifyWallpaperSurfaceContract)
}
