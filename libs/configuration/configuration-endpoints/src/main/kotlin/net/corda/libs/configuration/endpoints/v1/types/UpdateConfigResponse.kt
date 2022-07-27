package net.corda.libs.configuration.endpoints.v1.types

/**
 * The data object received via HTTP in response to a request to update cluster configuration.
 *
 * @property section The configuration section for which an update was requested.
 * @property config The current (i.e. post-update) configuration in JSON format for the given section.
 * @property schemaVersion The current configuration's schema version for the given section.
 * @property version The current configuration's optimistic-locking version for the given section.
 */
data class UpdateConfigResponse(
    val section: String,
    val config: String,
    val schemaVersion: ConfigSchemaVersion,
    val version: Int
)

