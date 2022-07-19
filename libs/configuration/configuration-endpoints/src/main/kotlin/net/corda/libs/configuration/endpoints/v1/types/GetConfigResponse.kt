package net.corda.libs.configuration.endpoints.v1.types

import net.corda.v5.base.versioning.Version

/**
 * The data object received via HTTP in response to a request to update cluster configuration.
 *
 * @property section The configuration section for which an update was requested.
 * @property sourceConfig The current configuration, as persisted in the DB, in JSON format for the given section.
 * @property configWithDefaults The current configuration, with defaults applied, in JSON format for the given section.
 * @property schemaVersion The current configuration's schema version for the given section.
 * @property version The current configuration's optimistic-locking version for the given section.
 */
data class GetConfigResponse(
    val section: String,
    val sourceConfig: String,
    val configWithDefaults: String,
    val schemaVersion: Version,
    val version: Int
)