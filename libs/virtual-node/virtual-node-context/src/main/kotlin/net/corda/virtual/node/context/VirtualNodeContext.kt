package net.corda.virtual.node.context

import net.corda.packaging.CPI

/**
 * A data only 'context' class containing relevant "virtual node" information.
 *
 * Also see https://github.com/corda/platform-eng-design/blob/mnesbit-rpc-apis/core/corda-5/corda-5.1/rpc-apis/rpc_api.md#cluster-database
 */
data class VirtualNodeContext(
    val id : String,
    val cpi : CPI.Identifier,
    val holdingIdentity : HoldingIdentity,
    // val tlsContext : TLSContext,
    // val p2pContext : P2PContext,
    // etc.
)
