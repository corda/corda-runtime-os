package net.corda.virtualnode.impl

import net.corda.data.identity.HoldingIdentity
import net.corda.data.virtualnode.VirtualNodeInfo
import net.corda.virtualnode.toCorda
import java.util.Collections

/**
 * Map of [HoldingIdentity] to [VirtualNodeInfo] AVRO data objects
 *
 * We use the [toCorda()] methods to convert the Avro objects to Corda ones.
 */
internal class VirtualNodeInfoMap {
    private val virtualNodeInfoByHoldingIdentity: MutableMap<HoldingIdentity, VirtualNodeInfo> =
        Collections.synchronizedMap(mutableMapOf())
    private val virtualNodeInfoById: MutableMap<String, MutableSet<VirtualNodeInfo>> =
        Collections.synchronizedMap(mutableMapOf())

    /**
     * Inner class to be used as a key for putting items.
     */
    data class Key(val holdingIdentity: HoldingIdentity, val id: String)

    /**
     * Get the Avro objects as Corda objects
     */
    fun getAllAsCorda(): Map<net.corda.virtualnode.HoldingIdentity, net.corda.virtualnode.VirtualNodeInfo> =
        virtualNodeInfoByHoldingIdentity
            .mapKeys { it.key.toCorda() }
            .mapValues { it.value.toCorda() }

    /**
     * Put (store/merge) the incoming map
     */
    fun putAll(incoming: Map<Key, VirtualNodeInfo>) =
        incoming.forEach { (key, value) -> put(key, value) }

    /**
     * Put [VirtualNodeInfo] into internal maps.
     */
    private fun putValue(key: Key, value: VirtualNodeInfo) {
        virtualNodeInfoById.getOrPut(key.id) { mutableSetOf() }.add(value)
        virtualNodeInfoByHoldingIdentity[key.holdingIdentity] = value
    }

    /**
     * Putting a null value removes the [VirtualNodeInfo] from the maps.
     */
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

    /**
     * Get a [VirtualNodeInfo] by [HoldingIdentity]
     */
    fun get(holdingIdentity: HoldingIdentity): VirtualNodeInfo? = virtualNodeInfoByHoldingIdentity[holdingIdentity]

    /**
     * Get a list of [VirtualNodeInfo] by short hash ([net.corda.virtual.node.info.HoldingIdentity.id])
     *
     * Usually a list of 1, but there's always a tiny chance of collisions.
     */
    fun getById(id: String): List<VirtualNodeInfo>? = virtualNodeInfoById[id]?.toList()

    /**
     * Remove the [VirtualNodeInfo] for a given key, specifically from the 'by id' map, in this method.
     */
    private fun removeById(key: Key) {
        // yes, this is an xor
        if (virtualNodeInfoById.containsKey(key.id) xor virtualNodeInfoByHoldingIdentity.containsKey(key.holdingIdentity)) {
            throw IllegalArgumentException("Keys should be present or missing in both maps - found key in only one map")
        }

        val virtualNodeInfo = virtualNodeInfoByHoldingIdentity[key.holdingIdentity] ?: return
        val values = virtualNodeInfoById[key.id] ?: return

        // remove the set entry for the specific VirtualNodeInfo for this holding id
        values.remove(virtualNodeInfo)

        // remove the map entry if there are no VirtualNodeInfo objects left for this id.
        if (values.isEmpty()) virtualNodeInfoById.remove(key.id)
    }

    /**
     * Remove the [VirtualNodeInfo] from this collection and return it.
     */
    fun remove(key: Key): VirtualNodeInfo? {
        removeById(key)
        return virtualNodeInfoByHoldingIdentity.remove(key.holdingIdentity)
    }
}
