import org.jetbrains.gradle.ext.settings
import org.jetbrains.gradle.ext.taskTriggers

plugins {
    kotlin("jvm") version "2.3.0"
    kotlin("kapt") version "2.3.0"
    kotlin("plugin.serialization") version "2.3.0"
    id("com.gradleup.shadow") version "9.3.1"
    id("eclipse")
    id("org.jetbrains.gradle.plugin.idea-ext") version "1.1.8"
    id("xyz.jpenilla.run-velocity") version "2.3.1"
}

val copyJarPath = project.findProperty("proxy_velocity_plugin_path").toString()
group = "com.nexomc"
version = "1.0"

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://repo.opencollab.dev/maven-snapshots")
    maven("https://repo.nexomc.com/releases/")
    maven("https://repo.nexomc.com/snapshots/")
    mavenLocal()
}

dependencies {
    compileOnly("com.velocitypowered:velocity-api:3.5.0-SNAPSHOT")
    compileOnly("com.velocitypowered:velocity-proxy:3.5.0-SNAPSHOT")
    compileOnly("io.netty:netty-all:4.2.10.Final")

    kapt("com.velocitypowered:velocity-api:3.5.0-SNAPSHOT")
    implementation("com.charleskorn.kaml:kaml:0.67.0")
    implementation("org.bstats:bstats-velocity:3.1.0")
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")
    implementation("team.unnamed:creative-api:1.13.0")
    implementation("team.unnamed:creative-serializer-minecraft:1.13.0")
}

tasks {
    runVelocity {
        // Configure the Velocity version for our task.
        // This is the only required configuration besides applying the plugin.
        // Your plugin's jar (or shadowJar if present) will be used automatically.
        velocityVersion("3.5.0-SNAPSHOT")
    }

    shadowJar {
        relocate("org.bstats", "com.nexomc.nexoproxy.bstats")
        relocate("team.unnamed", "com.nexomc.nexoproxy.unnamed")
        destinationDirectory.set(File(copyJarPath))
    }

    build.get().dependsOn(shadowJar)
}

val targetJavaVersion = 21
kotlin {
    jvmToolchain(targetJavaVersion)
}

val templateSource = file("src/main/templates")
val templateDest = layout.buildDirectory.dir("generated/sources/templates")
val generateTemplates = tasks.register<Copy>("generateTemplates") {
    val props = mapOf("version" to project.version)
    inputs.properties(props)

    from(templateSource)
    into(templateDest)
    expand(props)
}

sourceSets.main.configure { java.srcDir(generateTemplates.map { it.outputs }) }

project.idea.project.settings.taskTriggers.afterSync(generateTemplates)
project.eclipse.synchronizationTasks(generateTemplates)
