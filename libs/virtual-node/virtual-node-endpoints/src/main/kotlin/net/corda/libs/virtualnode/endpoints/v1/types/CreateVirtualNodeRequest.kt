package net.corda.libs.virtualnode.endpoints.v1.types

import net.corda.rest.JsonObject

/**
 * The data object sent via REST to request the creation of a virtual node.
 *
 * @param x500Name The X500 name for the new virtual node.
 * @param cpiFileChecksum The checksum of the CPI file.
 */
data class CreateVirtualNodeRequest(
    val x500Name: String,
    var cpiFileChecksum: String,
    val vaultDdlConnection: JsonObject?,
    val vaultDmlConnection: JsonObject?,
    val cryptoDdlConnection: JsonObject?,
    val cryptoDmlConnection: JsonObject?,
    val uniquenessDdlConnection: JsonObject?,
    val uniquenessDmlConnection: JsonObject?
) {
    init {
        // Whilst checksum can be expressed with either upper or lower case characters and has the same logical meaning,
        // all the cache keys using uppercase representation of CPI checksum, therefore early during processing cycle
        // on REST server, it would make sense to switch to uppercase.
        cpiFileChecksum = cpiFileChecksum.uppercase()
    }
}
