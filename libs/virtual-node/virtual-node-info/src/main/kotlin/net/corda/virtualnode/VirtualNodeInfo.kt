package net.corda.virtualnode

import net.corda.libs.packaging.CpiIdentifier
import net.corda.packaging.CPI
import net.corda.packaging.converters.toAvro
import net.corda.packaging.converters.toCorda
import java.util.UUID

/**
 * Contains information relevant to a particular virtual node (a CPI and a holding identity).
 *
 * NOTE:  this object should contain information that does NOT require the full construction and instantiation of a CPI,
 * and is not specific to a particular CPI (e.g. a custom serializer, or custom crypto)
 *
 * This is intended to be returned (initially, and primarily) by the VirtualNodeInfoService which is a 'fast lookup' and
 * does NOT instantiate CPIs.
 *
 * Also see https://github.com/corda/platform-eng-design/blob/mnesbit-rpc-apis/core/corda-5/corda-5.1/rpc-apis/rpc_api.md#cluster-database
 */
data class VirtualNodeInfo(val holdingIdentity: HoldingIdentity, val cpiIdentifier: CpiIdentifier)

fun VirtualNodeInfo.toAvro(): net.corda.data.virtualnode.VirtualNodeInfo =
    with (holdingIdentity) {
        net.corda.data.virtualnode.VirtualNodeInfo(
            toAvro(),
            cpiIdentifier.toAvro(),
            vaultDdlConnectionId?.let{ vaultDdlConnectionId.toString() },
            vaultDmlConnectionId?.let{ vaultDmlConnectionId.toString() },
            cryptoDdlConnectionId?.let{ cryptoDdlConnectionId.toString() },
            cryptoDmlConnectionId?.let{ cryptoDmlConnectionId.toString() },
            hsmConnectionId?.let { hsmConnectionId.toString() }
        )
    }

fun net.corda.data.virtualnode.VirtualNodeInfo.toCorda(): VirtualNodeInfo {
    val holdingIdentity = holdingIdentity.toCorda()
    holdingIdentity.vaultDdlConnectionId = vaultDdlConnectionId?.let { UUID.fromString(vaultDdlConnectionId) }
    holdingIdentity.vaultDmlConnectionId = vaultDmlConnectionId?.let { UUID.fromString(vaultDmlConnectionId) }
    holdingIdentity.cryptoDdlConnectionId = cryptoDdlConnectionId?.let { UUID.fromString(cryptoDdlConnectionId) }
    holdingIdentity.cryptoDmlConnectionId = cryptoDmlConnectionId?.let { UUID.fromString(cryptoDmlConnectionId) }
    return VirtualNodeInfo(holdingIdentity, CpiIdentifier.fromAvro(cpiIdentifier))
}
