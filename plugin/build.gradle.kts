plugins {
    java
    id("com.gradleup.shadow") version "9.2.2"
}

group = "com.dasannn.framcit"
version = "1.0.0"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(25))
    }
}

repositories {
    mavenCentral()
    maven {
        name = "papermc"
        url = uri("https://repo.papermc.io/repository/maven-public/")
    }
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:26.2.build.123-stable")
    testImplementation("io.papermc.paper:paper-api:26.2.build.123-stable")
    implementation("com.twelvemonkeys.imageio:imageio-webp:3.15.0")
    implementation("com.twelvemonkeys.imageio:imageio-core:3.15.0")

    testImplementation(platform("org.junit:junit-bom:5.11.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
}

tasks.shadowJar {
    archiveBaseName.set("FraMCit")
    archiveClassifier.set("")
    archiveVersion.set(project.version.toString())

    relocate("com.twelvemonkeys", "com.dasannn.framcit.libs.com.twelvemonkeys")
    mergeServiceFiles()
}

tasks.build {
    dependsOn(tasks.shadowJar)
}
