plugins {
    java
    id("com.gradleup.shadow") version "9.4.1"
}

group = "dev.tobifrosch.turnstile"
version = "1.0.0"

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    compileOnly("com.velocitypowered:velocity-api:4.0.0")
    annotationProcessor("com.velocitypowered:velocity-api:4.0.0")

    implementation("com.zaxxer:HikariCP:5.1.0") {
        exclude(group = "org.slf4j") // provided by Velocity
    }
    implementation("org.postgresql:postgresql:42.7.7") {
        exclude(group = "org.checkerframework")
    }
    implementation("org.yaml:snakeyaml:2.2")

    testImplementation(platform("org.junit:junit-bom:5.11.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

java {
    sourceCompatibility = JavaVersion.VERSION_25
    targetCompatibility = JavaVersion.VERSION_25
}

tasks.withType<JavaCompile> {
    options.release.set(25)
    options.encoding = "UTF-8"
    options.compilerArgs.add("-Xlint:deprecation")
}

tasks.test {
    useJUnitPlatform()
}

tasks.shadowJar {
    archiveClassifier.set("")
    relocate("com.zaxxer.hikari", "dev.tobifrosch.turnstile.lib.hikari")
    relocate("org.postgresql", "dev.tobifrosch.turnstile.lib.postgresql")
    relocate("org.yaml.snakeyaml", "dev.tobifrosch.turnstile.lib.snakeyaml")
    mergeServiceFiles()
}

tasks.build {
    dependsOn(tasks.shadowJar)
}
