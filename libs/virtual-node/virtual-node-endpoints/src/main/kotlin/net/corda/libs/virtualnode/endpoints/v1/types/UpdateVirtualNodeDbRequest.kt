package net.corda.libs.virtualnode.endpoints.v1.types

import net.corda.rest.JsonObject

/**
 * The data object sent via REST to request the update of a virtual node DB connectivity parameters.
 */
data class UpdateVirtualNodeDbRequest(
    val vaultDdlConnection: JsonObject?,
    val vaultDmlConnection: JsonObject?,
    val cryptoDdlConnection: JsonObject?,
    val cryptoDmlConnection: JsonObject?,
    val uniquenessDdlConnection: JsonObject?,
    val uniquenessDmlConnection: JsonObject?
)
