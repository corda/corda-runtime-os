plugins {
    kotlin("kapt")
}

val pf4jVersion: String by project
val jacksonVersion: String by project
val junit5Version: String by project

dependencies {
    compileOnly(project(":api"))
    compileOnly(kotlin("stdlib"))

    compileOnly("org.pf4j:pf4j:${pf4jVersion}")
    kapt("org.pf4j:pf4j:${pf4jVersion}")

    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:$jacksonVersion")
    testImplementation("org.junit.jupiter:junit-jupiter-api:$junit5Version")
}