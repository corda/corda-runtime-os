package net.corda.virtual.node.context

import net.corda.packaging.CPI

/**
 * A "virtual node context" that contains information relevant to a particular virtual node (a CPI and a holding identity).
 *
 * NOTE:  this object should contain information that does NOT require the full construction and instantiation of a CPI,
 * and is not specific to a particular CPI (e.g. a custom serializer, or custom crypto)
 *
 * This is intended to be returned (initially, and primarily) by the VirtualNodeInfoService which is a 'fast lookup' and
 * does NOT instantiate CPIs.
 *
 * Also see https://github.com/corda/platform-eng-design/blob/mnesbit-rpc-apis/core/corda-5/corda-5.1/rpc-apis/rpc_api.md#cluster-database
 */
interface VirtualNodeContext {
    val id: String
    val cpi: CPI.Identifier
    val holdingIdentity: HoldingIdentity

    // other 'static' info to be added.
}
