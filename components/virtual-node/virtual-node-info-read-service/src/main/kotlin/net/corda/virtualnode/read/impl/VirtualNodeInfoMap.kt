package net.corda.virtualnode.read.impl

import net.corda.crypto.core.ShortHash
import net.corda.data.identity.HoldingIdentity
import net.corda.data.virtualnode.VirtualNodeInfo
import net.corda.virtualnode.toCorda
import java.util.concurrent.ConcurrentHashMap

/**
 * Map of [HoldingIdentity] to [VirtualNodeInfo] AVRO data objects
 *
 * We use the [toCorda()] methods to convert the Avro objects to Corda ones.
 *
 * This class is nothing more than two maps with different keys.
 */
internal class VirtualNodeInfoMap {
    private val virtualNodeInfoByHoldingIdentity =  ConcurrentHashMap<HoldingIdentity, VirtualNodeInfo>()
    private val virtualNodeInfoById = ConcurrentHashMap<ShortHash, VirtualNodeInfo>()

    /** Class to be used as a key for putting items. */
    data class Key(val holdingIdentity: HoldingIdentity, val holdingIdShortHash: ShortHash)

    /** Get everything as Corda objects NOT Avro objects */
    fun getAllAsCordaObjects(): Map<net.corda.virtualnode.HoldingIdentity, net.corda.virtualnode.VirtualNodeInfo> =
        virtualNodeInfoByHoldingIdentity.mapKeys { it.key.toCorda() }.mapValues { it.value.toCorda() }

    /** Put (store/merge) the incoming map.  May throw [IllegalArgumentException] */
    fun putAll(incoming: Map<Key, VirtualNodeInfo>) = incoming.forEach { (key, value) -> put(key, value) }

    /** Put [VirtualNodeInfo] into internal maps. May throw [IllegalArgumentException] */
    private fun putValue(key: Key, value: VirtualNodeInfo) {
        // The following are checks that "should never occur in production", i.e.
        // that the holding identity 'key' matches the holding identity in the 'value'.
        // Whoever posts (HoldingIdentity, VirtualNodeInfo) on to Kakfa, should have used:
        // (virtualNodeInfo.holdingIdentity, virtualNodeInfo).
        if (key.holdingIdentity != value.holdingIdentity) {
            throw IllegalArgumentException("Trying to add a VirtualNodeInfo with a mismatched HoldingIdentity: ($key , $value)")
        }
        if (virtualNodeInfoById.containsKey(key.holdingIdShortHash)
            && virtualNodeInfoById[key.holdingIdShortHash]?.holdingIdentity != value.holdingIdentity) {
            throw IllegalArgumentException("Cannot put different VirtualNodeInfo for same short hash value: " +
                    "(${key.holdingIdShortHash}, $key , $value)")
        }
        virtualNodeInfoById[key.holdingIdShortHash] = value
        virtualNodeInfoByHoldingIdentity[key.holdingIdentity] = value
    }

    /** Putting a null value removes the [VirtualNodeInfo] from the maps. May throw [IllegalArgumentException] */
    fun put(key: Key, value: VirtualNodeInfo?) {
        if (value != null) {
            putValue(key, value)
        } else {
            remove(key)
        }
    }

    /** Get list of [VirtualNodeInfo] for all virtual nodes. Returns an empty list if no virtual nodes are onboarded. */
    fun getAll(): List<VirtualNodeInfo> = virtualNodeInfoById.values.toList()

    /** Get a [VirtualNodeInfo] by [HoldingIdentity], `null` if not found. */
    fun get(holdingIdentity: HoldingIdentity): VirtualNodeInfo? = virtualNodeInfoByHoldingIdentity[holdingIdentity]

    /** Get a [VirtualNodeInfo] by short hash ([net.corda.virtualnode.HoldingIdentity.shortHash]), `null` if not found */
    fun getById(holdingIdShortHash: ShortHash): VirtualNodeInfo? = virtualNodeInfoById[holdingIdShortHash]

    /** Remove the [VirtualNodeInfo] for a given key, specifically from the 'by id' map, in this method. */
    private fun removeById(key: Key) {
        // yes, this is xor
        if (virtualNodeInfoById.containsKey(key.holdingIdShortHash) xor virtualNodeInfoByHoldingIdentity.containsKey(key.holdingIdentity)) {
            throw IllegalArgumentException("Keys should be present or missing in both maps - found key in only one map")
        }

        virtualNodeInfoById.remove(key.holdingIdShortHash)
    }

    /** Remove the [VirtualNodeInfo] from this collection and return it. */
    fun remove(key: Key): VirtualNodeInfo? {
        removeById(key)
        return virtualNodeInfoByHoldingIdentity.remove(key.holdingIdentity)
    }

    /** Clear all the content */
    fun clear() {
        virtualNodeInfoById.clear()
        virtualNodeInfoByHoldingIdentity.clear()
    }
}
