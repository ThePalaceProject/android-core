fun getGitHash(): String {
    val proc = ProcessBuilder("git", "rev-parse", "--short", "HEAD")
        .redirectOutput(ProcessBuilder.Redirect.PIPE)
        .redirectError(ProcessBuilder.Redirect.PIPE)
        .start()

    proc.waitFor(10L, TimeUnit.SECONDS)
    return proc.inputStream.bufferedReader().readText().trim()
}

android {
    buildFeatures {
        buildConfig = true
    }

    defaultConfig {
        buildConfigField("String", "SIMPLIFIED_GIT_COMMIT", "\"${getGitHash()}\"")
    }
}

dependencies {
    implementation(libs.androidx.core)
    implementation(libs.kotlin.stdlib)
    implementation(libs.slf4j)
}
