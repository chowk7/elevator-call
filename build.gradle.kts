plugins {
    id("com.android.application") version "8.4.0" apply false
    kotlin("android") version "1.9.20" apply false
}

tasks.register<Delete>("clean") {
    delete(rootProject.buildDir)
}
