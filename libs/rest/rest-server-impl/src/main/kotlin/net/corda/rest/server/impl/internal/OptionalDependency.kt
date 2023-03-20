package net.corda.rest.server.impl.internal

/**
 * Overrides [io.javalin.core.util.OptionalDependency]
 */
enum class OptionalDependency(
    val displayName: String,
    groupId: String,
    artifactId: String,
    val version: String
) {
    /**
     * Note: [version] must be aligned with [swaggeruiVersion] in Gradle properties
     */
    SWAGGERUI("Swagger UI", "org.webjars", "swagger-ui", "4.15.5");

    val symbolicName: String = "$groupId.$artifactId"
}
