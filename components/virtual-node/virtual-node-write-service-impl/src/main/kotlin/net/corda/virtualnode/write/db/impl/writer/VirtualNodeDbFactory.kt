package net.corda.virtualnode.write.db.impl.writer

import net.corda.crypto.core.ShortHash
import net.corda.db.connection.manager.VirtualNodeDbType

internal interface VirtualNodeDbFactory {
    /**
     * Creates [VirtualNodeDb]s using connection configurations from virtual node creation request
     *
     * @param holdingIdentityShortHash Holding identity ID (short hash)
     * @param request Virtual node creation request
     *
     * @return map of [VirtualNodeDbType]s to [VirtualNodeDb]s
     */
    fun createVNodeDbs(
        holdingIdentityShortHash: ShortHash,
        request: VirtualNodeConnectionStrings
    ): Map<VirtualNodeDbType, VirtualNodeDb>
}

internal data class VirtualNodeConnectionStrings(
    val vaultDdlConnection: String?,
    val vaultDmlConnection: String?,
    val cryptoDdlConnection: String?,
    val cryptoDmlConnection: String?,
    val uniquenessDdlConnection: String?,
    val uniquenessDmlConnection: String?
)
