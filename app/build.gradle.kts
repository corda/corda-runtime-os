val pf4jVersion: String by project
val pluginsDir: File by rootProject.extra
val appMainClass = "net.corda.cli.application.BootKt"

plugins {
    kotlin("jvm")
    application
}

dependencies {
    implementation(kotlin("stdlib"))
    implementation(project(":api"))
    implementation("org.apache.logging.log4j:log4j-api:2.14.0")
    implementation("org.apache.logging.log4j:log4j-core:2.14.0")
    implementation("org.slf4j:slf4j-log4j12:1.7.28")
    implementation ("org.pf4j:pf4j:${pf4jVersion}")
    implementation ("org.apache.commons:commons-lang3:3.5")
    api("info.picocli:picocli:4.5.2")
}

application {
    mainClass.set(appMainClass)
}

tasks.named<JavaExec>("run") {
    systemProperty("pf4j.pluginsDir", pluginsDir.absolutePath)
}

tasks.register<Jar>("fatJar") {
    dependsOn(tasks.named("compileKotlin"))
//    archiveClassifier.set("fat")

    from(sourceSets.main.get().output)

    dependsOn(configurations.runtimeClasspath)
    from({
        configurations.runtimeClasspath.get().filter { it.name.endsWith("jar") }.map { zipTree(it) }
    })
    manifest {
        attributes["Main-Class"] = appMainClass
    }

    archiveBaseName.set("corda-cli")
}
