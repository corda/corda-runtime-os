package net.corda.libs.configuration.endpoints.v1.types

import net.corda.rest.JsonObject

/**
 * The data object sent via HTTP to request a cluster configuration update.
 *
 * @property section Section of the configuration to be updated.
 * @property version Version number used for optimistic locking.
 * @property config Updated configuration in JSON or HOCON format.
 * @property schemaVersion Schema version of the configuration.
 */
data class UpdateConfigParameters(
    val section: String, val version: Int, val config: JsonObject, val schemaVersion: ConfigSchemaVersion
)