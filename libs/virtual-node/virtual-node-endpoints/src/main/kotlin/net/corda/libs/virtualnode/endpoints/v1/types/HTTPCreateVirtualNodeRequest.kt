package net.corda.libs.virtualnode.endpoints.v1.types

/**
 * The data object sent via HTTP to request the creation of a virtual node.
 *
 * @property section Section of the configuration to be updated.
 * @property version Version number used for optimistic locking.
 * @property config Updated configuration in JSON or HOCON format.
 * @property schemaVersion Schema version of the configuration.
 */
data class HTTPCreateVirtualNodeRequest(
    val section: String, val version: Int, val config: String, val schemaVersion: Int
)