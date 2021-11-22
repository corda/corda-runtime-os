package net.corda.virtualnode.impl

import net.corda.data.identity.HoldingIdentity
import net.corda.data.virtualnode.VirtualNodeInfo
import net.corda.virtualnode.toCorda
import java.util.Collections

/**
 * Map of [HoldingIdentity] to [VirtualNodeInfo] AVRO data objects
 *
 * We use the [toCorda()] methods to convert the Avro objects to Corda ones.
 *
 * This class is nothing more than two maps with different keys.
 */
internal class VirtualNodeInfoMap {
    private val virtualNodeInfoByHoldingIdentity: MutableMap<HoldingIdentity, VirtualNodeInfo> =
        Collections.synchronizedMap(mutableMapOf())
    private val virtualNodeInfoById: MutableMap<String, VirtualNodeInfo> =
        Collections.synchronizedMap(mutableMapOf())

    /** Class to be used as a key for putting items. */
    data class Key(val holdingIdentity: HoldingIdentity, val id: String)

    /** Get everything as Corda objects NOT Avro objects */
    fun getAllAsCordaObjects(): Map<net.corda.virtualnode.HoldingIdentity, net.corda.virtualnode.VirtualNodeInfo> =
        virtualNodeInfoByHoldingIdentity
            .mapKeys { it.key.toCorda() }
            .mapValues { it.value.toCorda() }

    /** Put (store/merge) the incoming map */
    fun putAll(incoming: Map<Key, VirtualNodeInfo>) =
        incoming.forEach { (key, value) -> put(key, value) }

    /** Put [VirtualNodeInfo] into internal maps. */
    private fun putValue(key: Key, value: VirtualNodeInfo) {
        if (virtualNodeInfoById.containsKey(key.id) && virtualNodeInfoById[key.id]?.holdingIdentity != value.holdingIdentity) {
            throw IllegalArgumentException("Cannot put different VirtualNodeInfo for same short hash value.")
        }
        virtualNodeInfoById[key.id] = value
        virtualNodeInfoByHoldingIdentity[key.holdingIdentity] = value
    }

    /** Putting a null value removes the [VirtualNodeInfo] from the maps. */
    fun put(key: Key, value: VirtualNodeInfo?) {
        if (value != null) {
            if (key.holdingIdentity != value.holdingIdentity) {
                throw IllegalArgumentException("Trying to add a VirtualNodeInfo for an incorrect HoldingIdentity")
            }
            putValue(key, value)
        } else {
            remove(key)
        }
    }

    /** Get a [VirtualNodeInfo] by [HoldingIdentity], `null` if not found. */
    fun get(holdingIdentity: HoldingIdentity): VirtualNodeInfo? = virtualNodeInfoByHoldingIdentity[holdingIdentity]

    /** Get a [VirtualNodeInfo] by short hash ([net.corda.virtualnode.HoldingIdentity.id]), `null` if not found */
    fun getById(id: String): VirtualNodeInfo? = virtualNodeInfoById[id]

    /** Remove the [VirtualNodeInfo] for a given key, specifically from the 'by id' map, in this method. */
    private fun removeById(key: Key) {
        // yes, this is xor
        if (virtualNodeInfoById.containsKey(key.id) xor virtualNodeInfoByHoldingIdentity.containsKey(key.holdingIdentity)) {
            throw IllegalArgumentException("Keys should be present or missing in both maps - found key in only one map")
        }

        virtualNodeInfoById.remove(key.id)
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
