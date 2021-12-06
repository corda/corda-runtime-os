package net.corda.virtualnode

import net.corda.packaging.CPI
import net.corda.packaging.converters.toAvro
import net.corda.packaging.converters.toCorda

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
data class VirtualNodeInfo(val holdingIdentity: HoldingIdentity, val cpi: CPI.Identifier)

fun VirtualNodeInfo.toAvro(): net.corda.data.virtualnode.VirtualNodeInfo =
    net.corda.data.virtualnode.VirtualNodeInfo(holdingIdentity.toAvro(), cpi.toAvro())

fun net.corda.data.virtualnode.VirtualNodeInfo.toCorda(): VirtualNodeInfo =
    VirtualNodeInfo(holdingIdentity.toCorda(), cpiIdentifier.toCorda())
