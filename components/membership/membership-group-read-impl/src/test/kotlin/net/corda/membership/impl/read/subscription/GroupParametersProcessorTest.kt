package net.corda.membership.impl.read.subscription

import net.corda.data.KeyValuePair
import net.corda.data.KeyValuePairList
import net.corda.data.identity.HoldingIdentity
import net.corda.data.membership.GroupParameters as GroupParametersAvro
import net.corda.layeredpropertymap.impl.LayeredPropertyMapFactoryImpl
import net.corda.membership.impl.read.cache.MemberDataCache
import net.corda.membership.lib.impl.GroupParametersImpl
import net.corda.membership.lib.impl.GroupParametersImpl.Companion.EPOCH_KEY
import net.corda.membership.lib.impl.GroupParametersImpl.Companion.MODIFIED_TIME_KEY
import net.corda.membership.lib.impl.GroupParametersImpl.Companion.MPV_KEY
import net.corda.messaging.api.records.Record
import net.corda.test.util.time.TestClock
import net.corda.v5.membership.GroupParameters
import net.corda.virtualnode.toCorda
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant

class GroupParametersProcessorTest {
    private companion object {
        val layeredPropertyMapFactory = LayeredPropertyMapFactoryImpl(emptyList())
        val groupParametersCache = MemberDataCache.Impl<GroupParameters>()
        lateinit var groupParametersProcessor: GroupParametersProcessor
        val clock = TestClock(Instant.ofEpochSecond(100))

        const val GROUP_ID = "groupId"
        val alice = HoldingIdentity("O=Alice, L=London, C=GB", GROUP_ID)
        val bob = HoldingIdentity("O=Bob, L=London, C=GB", GROUP_ID)
        val testEntries = mapOf(
            EPOCH_KEY to "1",
            MPV_KEY to "1",
            MODIFIED_TIME_KEY to clock.instant().toString()
        )
        val updatedTestEntries = mapOf(
            EPOCH_KEY to "2",
            MPV_KEY to "2",
            MODIFIED_TIME_KEY to clock.instant().toString()
        )
        val aliceAvroGroupParams: GroupParametersAvro = createAvroParams(alice)
        val bobAvroGroupParams: GroupParametersAvro = createAvroParams(bob)
        val updatedBobAvroGroupParams: GroupParametersAvro = createAvroParams(bob, updatedTestEntries)

        val groupParams = GroupParametersImpl(layeredPropertyMapFactory.createMap(testEntries))
        val updatedGroupParams = GroupParametersImpl(layeredPropertyMapFactory.createMap(updatedTestEntries))

        private fun convertToKeyValuePairList(data: Map<String, String>) = KeyValuePairList(
            data.entries.map {
                KeyValuePair(it.key, it.value)
            }
        )

        private fun createAvroParams(
            holdingIdentity: HoldingIdentity,
            groupParamEntries: Map<String, String> = testEntries
        ) = GroupParametersAvro(
            holdingIdentity,
            convertToKeyValuePairList(groupParamEntries)
        )
    }

    @BeforeEach
    fun setUp() {
        groupParametersProcessor = GroupParametersProcessor(groupParametersCache, layeredPropertyMapFactory)
    }

    @Test
    fun `key class is String`() {
        assertThat(groupParametersProcessor.keyClass).isEqualTo(String::class.java)
    }

    @Test
    fun `value class is GroupParameters`() {
        assertThat(groupParametersProcessor.valueClass).isEqualTo(GroupParametersAvro::class.java)
    }

    @Test
    fun `group params cache is successfully populated from group params topic on initial snapshot`() {
        groupParametersProcessor.onSnapshot(mapOf(alice.toCorda().shortHash.value to aliceAvroGroupParams))
        assertThat(groupParametersCache.get(alice.toCorda())).isEqualTo(groupParams)
    }

    @Test
    fun `group params cache is successfully updated with new record`() {
        groupParametersProcessor.onSnapshot(
            mapOf(
                alice.toCorda().shortHash.value to aliceAvroGroupParams
            )
        )
        with(groupParametersCache.getAll()) {
            assertThat(this.size).isEqualTo(1)
            assertThat(this.keys).containsExactlyInAnyOrder(alice.toCorda())
            assertThat(this.values).containsExactlyInAnyOrder(groupParams)
        }

        groupParametersProcessor.onNext(
            Record("topic", bob.toCorda().shortHash.value, bobAvroGroupParams),
            null,
            mapOf(
                alice.toCorda().shortHash.value to aliceAvroGroupParams
            )
        )
        with(groupParametersCache.getAll()) {
            assertThat(this.size).isEqualTo(2)
            assertThat(this.keys).containsExactlyInAnyOrder(alice.toCorda(), bob.toCorda())
            assertThat(this.values).containsExactlyInAnyOrder(groupParams, groupParams)
        }
    }

    @Test
    fun `group params cache is successfully updated with changed record`() {
        groupParametersProcessor.onSnapshot(
            mapOf(
                alice.toCorda().shortHash.value to aliceAvroGroupParams,
                bob.toCorda().shortHash.value to bobAvroGroupParams
            )
        )
        with(groupParametersCache.getAll()) {
            assertThat(this.size).isEqualTo(2)
            assertThat(this.keys).containsExactlyInAnyOrder(alice.toCorda(), bob.toCorda())
            assertThat(this.values).containsExactlyInAnyOrder(groupParams, groupParams)
        }

        groupParametersProcessor.onNext(
            Record("topic", bob.toCorda().shortHash.value, updatedBobAvroGroupParams),
            bobAvroGroupParams,
            mapOf(
                alice.toCorda().shortHash.value to aliceAvroGroupParams,
                bob.toCorda().shortHash.value to bobAvroGroupParams
            )
        )
        with(groupParametersCache.getAll()) {
            assertThat(this.size).isEqualTo(2)
            assertThat(this.keys).containsExactlyInAnyOrder(alice.toCorda(), bob.toCorda())
            assertThat(this.values).containsExactlyInAnyOrder(groupParams, updatedGroupParams)
        }
    }
}