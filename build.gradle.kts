plugins { id("com.android.library"); kotlin("android") }
android {
    namespace = "com.kigtts.engines.sensevoice"
    compileSdk = 36
    defaultConfig { minSdk = 26 }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlin { jvmToolchain(17) }
    testOptions { unitTests.isIncludeAndroidResources = true }
}
tasks.register<Copy>("collectPluginDependencies") {
    from(configurations.getByName("debugRuntimeClasspath"))
    into(layout.buildDirectory.dir("plugin-dependencies"))
}
dependencies {
    compileOnly(project(":plugin-android-api"))
    compileOnly(files("libs/sherpa-onnx-1.12.38-noort.aar"))
    implementation("com.microsoft.onnxruntime:onnxruntime-android:1.24.3")
    testImplementation(project(":plugin-android-api"))
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
}
