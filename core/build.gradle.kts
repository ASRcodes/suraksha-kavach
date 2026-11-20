plugins { alias(libs.plugins.kotlin.jvm) }
kotlin { jvmToolchain(17) }
dependencies {
    implementation(libs.kotlinx.json)
    testImplementation(libs.junit)
}
tasks.test { testLogging { events("failed", "skipped") } }
