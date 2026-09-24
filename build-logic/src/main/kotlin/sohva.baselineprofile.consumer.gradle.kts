import androidx.baselineprofile.gradle.consumer.BaselineProfileConsumerExtension

// Applied to :app: consumes the profiles :benchmark generates (plan/05 §4.9). Profiles are
// generated on purpose, reviewed and committed, never during an ordinary build.
plugins {
    id("androidx.baselineprofile")
}

extensions.configure<BaselineProfileConsumerExtension> {
    automaticGenerationDuringBuild = false
    saveInSrc = true
    mergeIntoMain = true
}

dependencies {
    "baselineProfile"(project(":benchmark"))
}
