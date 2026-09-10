plugins {
    java
    application
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(25))
    }
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("com.twelvemonkeys.imageio:imageio-webp:3.15.0")
    implementation("com.twelvemonkeys.imageio:imageio-core:3.15.0")
}

application {
    mainClass.set("com.dasannn.framcit.spike.SpikeWebp")
}
