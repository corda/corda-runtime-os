package net.corda.libs.configuration.endpoints.v1.types

/**
 * Represents the schema version which is used by configuration.
 * Not to be confused with revision version of configuration which is one integer that gets incremented every time
 * configuration is updated.
 */
data class ConfigSchemaVersion(val major: Int, val minor: Int)