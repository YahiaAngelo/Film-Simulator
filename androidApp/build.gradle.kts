plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.kotlinAndroid)
    alias(libs.plugins.compose.compiler)
}

kotlin {
    jvmToolchain(17)
}

// Skiko on Android ships its native libraries via the JetBrains compose-dev maven only
// (skiko-android-runtime-{arm64,x64}); they're not in skiko-android.aar. We pull each ABI
// jar into a private configuration, extract the .so, and feed the resulting directory back
// into android.jniLibs so AGP packages it. Bumping `skiko` in libs.versions.toml is the
// only thing required to refresh the (16-KB-aligned, since 0.144.0) binaries.
val skikoJni: Configuration by configurations.creating

dependencies {
    skikoJni("org.jetbrains.skiko:skiko-android-runtime-arm64:${libs.versions.skiko.get()}")
    skikoJni("org.jetbrains.skiko:skiko-android-runtime-x64:${libs.versions.skiko.get()}")
}

val abiByClassifier = mapOf(
    "skiko-android-runtime-arm64" to "arm64-v8a",
    "skiko-android-runtime-x64" to "x86_64",
)

abstract class ExtractSkikoJni : DefaultTask() {
    @get:InputFiles abstract val jars: ConfigurableFileCollection
    @get:Input    abstract val abiByJarName: MapProperty<String, String>
    @get:OutputDirectory abstract val outputDir: DirectoryProperty

    @get:javax.inject.Inject abstract val fs: FileSystemOperations
    @get:javax.inject.Inject abstract val archives: ArchiveOperations

    @TaskAction
    fun run() {
        val out = outputDir.get().asFile
        out.deleteRecursively()
        out.mkdirs()
        val mapping = abiByJarName.get()
        jars.forEach { jar ->
            val abi = mapping.entries.firstOrNull { jar.name.startsWith(it.key) }?.value
                ?: return@forEach
            fs.copy {
                from(archives.zipTree(jar)) { include("*.so") }
                into(out.resolve(abi))
            }
        }
    }
}

val extractSkikoJni = tasks.register<ExtractSkikoJni>("extractSkikoJni") {
    jars.from(skikoJni)
    abiByJarName.set(abiByClassifier)
    outputDir.set(layout.buildDirectory.dir("skiko-jni-libs"))
}

android {
    namespace = "io.github.yahiaangelo.filmsimulator.android"
    compileSdk = 37
    defaultConfig {
        applicationId = "io.github.yahiaangelo.filmsimulator.android"
        minSdk = 24
        targetSdk = 37
        versionCode = 15
        versionName = "0.6.3"
    }
    buildFeatures {
        compose = true
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "/META-INF/versions/**"
        }
    }
    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

androidComponents {
    onVariants { variant ->
        variant.sources.jniLibs?.addGeneratedSourceDirectory(
            taskProvider = extractSkikoJni,
            wiredWith = ExtractSkikoJni::outputDir,
        )
    }
}

dependencies {
    implementation(projects.shared)
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.androidx.activity.compose)
    debugImplementation(libs.compose.ui.tooling)
    implementation(libs.ktor.client.android)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.koin.android)
}