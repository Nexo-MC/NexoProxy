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
version = "1.2.1"

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://repo.opencollab.dev/maven-snapshots")
    maven("https://repo.nexomc.com/releases/")
    maven("https://repo.nexomc.com/snapshots/")
    maven("https://repo.william278.net/releases")
    maven("https://oss.sonatype.org/content/repositories/snapshots") // BungeeCord transitive deps
    maven("https://libraries.minecraft.net")                          // BungeeCord transitive deps
    mavenLocal()
}

dependencies {
    compileOnly("com.velocitypowered:velocity-api:3.5.0-SNAPSHOT")
    compileOnly("com.velocitypowered:velocity-proxy:3.5.0-SNAPSHOT")
    compileOnly("io.netty:netty-all:4.2.10.Final")
    compileOnly("net.william278:velocitab:1.5.2")
    compileOnly("net.william278:velocityscoreboardapi:2.0.0")
    // BungeeCord support: resource pack dedup only (see BUNGEE.md) - no typed packet API for resource
    // pack push/pop, unlike Velocity, so that part reads/writes raw packets directly.
    compileOnly("net.md-5:bungeecord-api:1.21-R0.4")

    kapt("com.velocitypowered:velocity-api:3.5.0-SNAPSHOT")
    implementation("com.charleskorn.kaml:kaml:0.67.0")
    implementation("org.bstats:bstats-velocity:3.1.0")
    implementation("org.bstats:bstats-bungeecord:3.1.0")
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")
    implementation("team.unnamed:creative-api:1.13.0")
    implementation("team.unnamed:creative-serializer-minecraft:1.13.0")
    // BungeeCord doesn't provide Adventure at runtime like Velocity does, so it has to be bundled here.
    // Pinned to the same version Velocity's own dependency chain resolves to (see compileClasspath), and
    // deliberately NOT relocated: relocating net.kyori would rewrite the shared GlyphHandler.kt's own
    // bytecode references too, breaking it on Velocity (whose platform-provided Adventure classes stay
    // at net.kyori.*, unrelocated). These are compileOnly on the Velocity side, so nothing of Velocity's
    // ever ends up bundled here to begin with - only Bungee's copy does.
    implementation("net.kyori:adventure-api:4.26.1")
    implementation("net.kyori:adventure-text-serializer-gson:4.26.1")

    testImplementation(kotlin("test-junit5"))
    // compileOnly isn't visible to the test source set by default, and tests actually construct real
    // BaseComponent/TextComponent instances (see BungeeGlyphsTest), so this needs runtime too, not just
    // compile-time visibility.
    testCompileOnly("net.md-5:bungeecord-api:1.21-R0.4")
    testRuntimeOnly("net.md-5:bungeecord-api:1.21-R0.4")
}

tasks {
    test {
        useJUnitPlatform()
    }

    runVelocity {
        // Configure the Velocity version for our task.
        // This is the only required configuration besides applying the plugin.
        // Your plugin's jar (or shadowJar if present) will be used automatically.
        velocityVersion("3.5.0-SNAPSHOT")
    }

    processResources {
        filesMatching("bungee.yml") {
            expand("version" to project.version)
        }
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
