package net.corda.httprpc.server.impl.internal

/**
 * Overrides [io.javalin.core.util.OptionalDependency]
 */
enum class OptionalDependency(
    val displayName: String,
    val testClass: String,
    val groupId: String,
    val artifactId: String,
    val version: String
) {
    /**
     * Note: [version] must be aligned with [swaggeruiVersion] in Gradle properties
     */
    SWAGGERUI("Swagger UI", "STATIC-FILES", "org.webjars", "swagger-ui", "4.1.3");

    val symbolicName: String = "$groupId.$artifactId"
}
