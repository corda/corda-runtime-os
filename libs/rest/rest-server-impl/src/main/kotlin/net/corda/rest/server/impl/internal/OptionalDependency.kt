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
     * Note: [version] must be aligned with `swaggeruiVersion` in `gradle/libs.versions.toml`
     */
    SWAGGERUI("Swagger UI", "org.webjars", "swagger-ui", "5.15.1");

    val symbolicName: String = "$groupId.$artifactId"
}
