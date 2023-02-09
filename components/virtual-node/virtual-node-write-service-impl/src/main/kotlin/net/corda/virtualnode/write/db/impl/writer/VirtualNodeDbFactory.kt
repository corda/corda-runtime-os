package net.corda.virtualnode.write.db.impl.writer

import net.corda.data.virtualnode.VirtualNodeCreateRequest
import net.corda.db.connection.manager.VirtualNodeDbType
import net.corda.virtualnode.ShortHash

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
        request: VirtualNodeCreateRequest
    ): Map<VirtualNodeDbType, VirtualNodeDb>
}
