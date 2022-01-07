package net.corda.configuration.rpcops.impl.v1.types

import net.corda.data.config.ConfigurationManagementRequest

/**
 * The data object sent via HTTP to request a cluster configuration update.
 *
 * @property section Section of the configuration to be updated.
 * @property version Version number used for optimistic locking.
 * @property config Updated configuration in JSON or HOCON format.
 * @property schemaVersion Schema version of the configuration.
 */
internal data class HTTPUpdateConfigRequest(
    val section: String,
    val version: Int,
    val config: String,
    val schemaVersion: Int
) {
    internal fun toRPCRequest(actor: String) =
        ConfigurationManagementRequest(section, version, config, schemaVersion, actor)
}