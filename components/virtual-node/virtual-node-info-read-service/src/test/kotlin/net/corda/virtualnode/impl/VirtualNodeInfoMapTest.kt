package net.corda.virtualnode.impl

import net.corda.libs.packaging.core.CpiIdentifier
import net.corda.v5.crypto.SecureHash
import net.corda.virtualnode.HoldingIdentity
import net.corda.virtualnode.VirtualNodeInfo
import net.corda.virtualnode.read.impl.VirtualNodeInfoMap
import net.corda.virtualnode.toAvro
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.UUID

/** Testing AVRO objects, so be sure to add `toAvro()`  (but using corda objects)
 *
 */
class VirtualNodeInfoMapTest {
    private lateinit var map: VirtualNodeInfoMap

    private val secureHash = SecureHash("algorithm", "1234".toByteArray())
    private val fakeShortHash = "BEEFDEADBEEF"
    private val otherShortHash = "F0000000000D"
    private val cpiIdentifier = CpiIdentifier("ghi", "hjk", secureHash)

    @BeforeEach
    fun beforeEach() {
        map = VirtualNodeInfoMap()
    }

    @Test
    fun `put one VirtualNodeInfo`() {
        val holdingIdentity = HoldingIdentity("abc", UUID.randomUUID().toString())
        val virtualNodeInfo = VirtualNodeInfo(holdingIdentity, cpiIdentifier, null, UUID.randomUUID(), null, UUID.randomUUID())

        map.put(
            VirtualNodeInfoMap.Key(holdingIdentity.toAvro(), fakeShortHash),
            virtualNodeInfo.toAvro()
        )

        assertThat(map.get(holdingIdentity.toAvro())).isEqualTo(virtualNodeInfo.toAvro())
        assertThat(map.getById(fakeShortHash)).isNotNull
        assertThat(map.getById(fakeShortHash))?.isEqualTo(virtualNodeInfo.toAvro())
    }

    @Test
    fun `put two VirtualNodeInfo`() {
        val holdingIdentity = HoldingIdentity("abc", UUID.randomUUID().toString())
        val virtualNodeInfo = VirtualNodeInfo(holdingIdentity, cpiIdentifier, null, UUID.randomUUID(), null, UUID.randomUUID())
        map.put(
            VirtualNodeInfoMap.Key(holdingIdentity.toAvro(), fakeShortHash),
            virtualNodeInfo.toAvro()
        )

        val otherHoldingIdentity = HoldingIdentity("abc", UUID.randomUUID().toString())
        val otherVirtualNode = VirtualNodeInfo(otherHoldingIdentity, cpiIdentifier, null, UUID.randomUUID(), null, UUID.randomUUID())
        map.put(
            VirtualNodeInfoMap.Key(otherHoldingIdentity.toAvro(), otherShortHash),
            otherVirtualNode.toAvro()
        )

        assertThat(map.get(holdingIdentity.toAvro())).isEqualTo(virtualNodeInfo.toAvro())
        assertThat(map.getById(fakeShortHash)).isNotNull
        assertThat(map.getById(fakeShortHash))?.isEqualTo(virtualNodeInfo.toAvro())

        assertThat(map.get(otherHoldingIdentity.toAvro())).isEqualTo(otherVirtualNode.toAvro())
        assertThat(map.getById(otherShortHash)).isNotNull
        assertThat(map.getById(otherShortHash))?.isEqualTo(otherVirtualNode.toAvro())
    }

    @Test
    fun `put two VirtualNodeInfo with clashing ids`() {
        val holdingIdentity = HoldingIdentity("abc", UUID.randomUUID().toString())
        val virtualNodeInfo = VirtualNodeInfo(holdingIdentity, cpiIdentifier, null, UUID.randomUUID(), null, UUID.randomUUID())
        map.put(
            VirtualNodeInfoMap.Key(holdingIdentity.toAvro(), fakeShortHash),
            virtualNodeInfo.toAvro()
        )

        val otherHoldingIdentity = HoldingIdentity("abc", UUID.randomUUID().toString())
        val otherVirtualNodeInfo = VirtualNodeInfo(otherHoldingIdentity, cpiIdentifier, null, UUID.randomUUID(), null, UUID.randomUUID())
        // Put with same short hash
        assertThrows<IllegalArgumentException> {
            map.put(
                VirtualNodeInfoMap.Key(otherHoldingIdentity.toAvro(), fakeShortHash),
                otherVirtualNodeInfo.toAvro()
            )
        }

        assertThat(map.get(holdingIdentity.toAvro())).isEqualTo(virtualNodeInfo.toAvro())
        assertThat(map.getById(fakeShortHash)).isNotNull
    }

    /**
     * This scenario should NEVER occur in production, and should be caught in development.
     */
    @Test
    fun `putting mismatched HoldingIdentity throws`() {
        val holdingIdentity = HoldingIdentity("abc", UUID.randomUUID().toString())
        val differentHoldingIdentity = HoldingIdentity("abc", UUID.randomUUID().toString())
        val virtualNodeInfo = VirtualNodeInfo(differentHoldingIdentity, cpiIdentifier, null, UUID.randomUUID(), null, UUID.randomUUID())
        assertThrows<IllegalArgumentException> {
            map.put(
                VirtualNodeInfoMap.Key(
                    holdingIdentity.toAvro(),
                    fakeShortHash
                ), virtualNodeInfo.toAvro()
            )
        }
    }

    @Test
    fun `put one and remove one VirtualNodeInfo`() {
        val holdingIdentity = HoldingIdentity("abc", UUID.randomUUID().toString())
        val virtualNodeInfo = VirtualNodeInfo(holdingIdentity, cpiIdentifier, null, UUID.randomUUID(), null, UUID.randomUUID())

        val key = VirtualNodeInfoMap.Key(holdingIdentity.toAvro(), fakeShortHash)
        map.put(key, virtualNodeInfo.toAvro())

        assertThat(map.getById(fakeShortHash))?.isEqualTo(virtualNodeInfo.toAvro())

        val actualVirtualNodeInfo = map.remove(key)
        assertThat(actualVirtualNodeInfo).isEqualTo(virtualNodeInfo.toAvro())
    }

    @Test
    fun `put two and remove two VirtualNodeInfo`() {
        val holdingIdentity = HoldingIdentity("abc", UUID.randomUUID().toString())
        val virtualNodeInfo = VirtualNodeInfo(holdingIdentity, cpiIdentifier, null, UUID.randomUUID(), null, UUID.randomUUID())
        val key = VirtualNodeInfoMap.Key(holdingIdentity.toAvro(), fakeShortHash)
        map.put(key, virtualNodeInfo.toAvro())

        val otherHoldingIdentity = HoldingIdentity("abc", UUID.randomUUID().toString())
        val otherVirtualNodeInfo = VirtualNodeInfo(otherHoldingIdentity, cpiIdentifier, null, UUID.randomUUID(), null, UUID.randomUUID())
        val otherFakeShortHash = "F000000D"
        val otherKey =
            VirtualNodeInfoMap.Key(otherHoldingIdentity.toAvro(), otherFakeShortHash)
        map.put(otherKey, otherVirtualNodeInfo.toAvro())

        assertThat(map.getById(fakeShortHash))?.isEqualTo(virtualNodeInfo.toAvro())
        assertThat(map.getById(otherFakeShortHash))?.isEqualTo(otherVirtualNodeInfo.toAvro())

        // Remove first item
        val actualVirtualNodeInfo = map.remove(key)
        assertThat(actualVirtualNodeInfo).isEqualTo(virtualNodeInfo.toAvro())

        assertThat(map.getById(fakeShortHash)).isNull()
        assertThat(map.getById(otherFakeShortHash))?.isEqualTo(otherVirtualNodeInfo.toAvro())

        // Remove second item
        val actualOtherVirtualNodeInfo = map.remove(otherKey)
        assertThat(actualOtherVirtualNodeInfo).isEqualTo(otherVirtualNodeInfo.toAvro())

        assertThat(map.getById(fakeShortHash)).isNull()
        assertThat(map.getById(otherFakeShortHash)).isNull()
    }

    @Test
    fun `second remove returns null`() {
        val holdingIdentity = HoldingIdentity("abc", UUID.randomUUID().toString())
        val virtualNodeInfo = VirtualNodeInfo(holdingIdentity, cpiIdentifier, null, UUID.randomUUID(), null, UUID.randomUUID())
        val key = VirtualNodeInfoMap.Key(holdingIdentity.toAvro(), fakeShortHash)
        map.put(key, virtualNodeInfo.toAvro())

        // remove once
        val actualVirtualNodeInfo = map.remove(key)
        assertThat(actualVirtualNodeInfo).isEqualTo(virtualNodeInfo.toAvro())

        // remove twice
        assertThat(map.remove(key)).isNull()
    }

    @Test
    fun `test returning map as corda types`() {
        //  **Tiny** chance this method could produce clashing hash codes from [HoldingIdentity.id]

        val keys = mutableListOf<VirtualNodeInfoMap.Key>()
        val count = 100

        // Add a number of VirtualNodeInfo objects to the map, and keep a copy of the keys
        for (i in 0..count) {
            val holdingIdentity = HoldingIdentity("abc", UUID.randomUUID().toString())
            val virtualNodeInfo = VirtualNodeInfo(holdingIdentity, cpiIdentifier, null, UUID.randomUUID(), null, UUID.randomUUID())
            // Actually use the real short hash/id of the holding identity
            val key = VirtualNodeInfoMap.Key(holdingIdentity.toAvro(), holdingIdentity.id)
            keys.add(key)
            map.put(key, virtualNodeInfo.toAvro())
        }

        // Check that we've added them
        for (i in 0..count) {
            assertThat(map.get(keys[i].holdingIdentity)).isNotNull
            assertThat(map.getById(keys[i].id)).isNotNull
            assertThat(map.get(keys[i].holdingIdentity)!!.holdingIdentity!!).isEqualTo(keys[i].holdingIdentity)
        }

        // GET THE ENTIRE CONTENT OF THE MAP AS CORDA TYPES AND CHECK THAT TOO.
        val allVirtualNodeInfos = map.getAllAsCordaObjects()

        allVirtualNodeInfos.forEach { (k, v) ->
            assertThat(map.get(k.toAvro())).isNotNull
            assertThat(map.get(k.toAvro())).isEqualTo(v.toAvro())

            assertThat(map.getById(k.id)).isNotNull
            assertThat(map.getById(k.id))!!.isEqualTo(v.toAvro())
        }

        // Remove them
        for (i in 0..count) {
            val actualVirtualNodeInfo = map.remove(keys[i])
            assertThat(actualVirtualNodeInfo!!.holdingIdentity).isEqualTo(keys[i].holdingIdentity)
        }

        // Check they've been removed.
        for (i in 0..count) {
            val actualVirtualNodeInfo = map.remove(keys[i])
            assertThat(actualVirtualNodeInfo).isNull()
        }

        // Check they've been removed using the copy of the map:
        // Get the entire content of the map and check that too.
        allVirtualNodeInfos.forEach { (k, _) ->
            assertThat(map.get(k.toAvro())).isNull()
            // even if we had clashing hashes, we should have removed them and the last one should
            // remove the list and return null
            assertThat(map.getById(k.id)).isNull()
        }
    }

    @Test
    fun `use corda types`() {
        //  **Tiny** chance this method could produce clashing hash codes from [HoldingIdentity.id]
        // but the test should succeed nonetheless.

        val count = 100

        for (i in 0..count) {
            val holdingIdentity = HoldingIdentity("abc", UUID.randomUUID().toString())
            val virtualNodeInfo = VirtualNodeInfo(holdingIdentity, cpiIdentifier, null, UUID.randomUUID(), null, UUID.randomUUID())
            // Actually use the real short hash/id of the holding identity
            val key = VirtualNodeInfoMap.Key(holdingIdentity.toAvro(), holdingIdentity.id)
            map.put(key, virtualNodeInfo.toAvro())
        }

        val allVirtualNodeInfos = map.getAllAsCordaObjects()

        allVirtualNodeInfos.forEach { (k, v) ->
            assertThat(map.get(k.toAvro())).isNotNull
            assertThat(map.get(k.toAvro())).isEqualTo(v.toAvro())

            assertThat(map.getById(k.id)).isNotNull
            assertThat(map.getById(k.id))!!.isEqualTo(v.toAvro())
        }

        allVirtualNodeInfos.forEach { (k, _) ->
            map.remove(
                VirtualNodeInfoMap.Key(
                    k.toAvro(),
                    k.id
                )
            )
        }

        // even if we had clashing hashes, we should have removed them and the last one should
        // remove the list and return null
        allVirtualNodeInfos.forEach { (k, _) ->
            assertThat(map.get(k.toAvro())).isNull()
            assertThat(map.getById(k.id)).isNull()
        }
    }
}
