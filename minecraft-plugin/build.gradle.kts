plugins {
    java
    id("com.github.johnrengelman.shadow") version "8.1.1"
}

group = "com.deuterium"
version = "0.1.0"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
}

sourceSets {
    main {
        resources {
            if (file("src/main/local-resources").exists()) {
                setSrcDirs(listOf("src/main/local-resources", "src/main/resources"))
            }
        }
    }
}

tasks.processResources {
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}

dependencies {
    compileOnly("org.spigotmc:spigot-api:1.20.1-R0.1-SNAPSHOT")
    compileOnly("com.github.MilkBowl:VaultAPI:1.7")
    implementation("org.java-websocket:Java-WebSocket:1.5.7")
    implementation("org.json:json:20240303")
}

tasks.shadowJar {
    archiveClassifier.set("")
    relocate("org.java_websocket", "com.deuterium.plugin.libs.org.java_websocket")
    relocate("org.json", "com.deuterium.plugin.libs.org.json")
}

tasks.build {
    dependsOn(tasks.shadowJar)
}

