plugins {
    java
    id("com.gradleup.shadow") version "9.2.2"
}

group = "com.dasannn.framcit"
version = "1.1.0"

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
    maven {
        name = "jitpack"
        url = uri("https://jitpack.io")
    }
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:26.3.build.218-stable")
    testImplementation("io.papermc.paper:paper-api:26.3.build.218-stable")
    compileOnly("com.github.MilkBowl:VaultAPI:1.7") {
        exclude(group = "org.bukkit", module = "bukkit")
    }
    testImplementation("com.github.MilkBowl:VaultAPI:1.7") {
        exclude(group = "org.bukkit", module = "bukkit")
    }
    implementation("com.twelvemonkeys.imageio:imageio-webp:3.15.0")
    implementation("com.twelvemonkeys.imageio:imageio-core:3.15.0")
    implementation("com.google.code.gson:gson:2.14.0")

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
    relocate("com.google.gson", "com.dasannn.framcit.libs.com.google.gson")
    mergeServiceFiles()
}

tasks.build {
    dependsOn(tasks.shadowJar)
}
