package net.corda.virtual.node.context

import net.corda.packaging.CPI

/**
 * A "virtual node context" that contains information relevant to a particular virtual node (a CPI and a holding identity).
 *
 * NOTE:  this object should contain information that does NOT require the full construction and instantiation of a CPI.
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

    /**
     * Get an object into the internal storage using the given key.
     *
     * Returns null if it doesn't exist
     *
     * Throws [TypeCastException] if the object cannot be cast to the expected type.
     */
    fun <T> getObject(key: String) : T?

    /**
     * Put an object into the internal storage using the given key.
     *
     * Overwrites existing value.
     */
    fun <T> putObject(key: String, value: T)
}
