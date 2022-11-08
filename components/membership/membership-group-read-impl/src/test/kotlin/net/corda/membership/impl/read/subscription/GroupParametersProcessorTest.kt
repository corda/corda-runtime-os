package net.corda.membership.impl.read.subscription

import net.corda.data.KeyValuePair
import net.corda.data.KeyValuePairList
import net.corda.data.identity.HoldingIdentity
import net.corda.layeredpropertymap.LayeredPropertyMapFactory
import net.corda.layeredpropertymap.testkit.LayeredPropertyMapMocks
import net.corda.data.membership.GroupParameters as GroupParametersAvro
import net.corda.membership.impl.read.cache.MemberDataCache
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
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import java.time.Instant

class GroupParametersProcessorTest {
    private companion object {
        val layeredPropertyMapFactory: LayeredPropertyMapFactory = LayeredPropertyMapMocks.createFactory()
        val groupParametersCache = MemberDataCache.Impl<GroupParameters>()
        lateinit var groupParametersProcessor: GroupParametersProcessor
        val clock = TestClock(Instant.ofEpochSecond(100))

        const val GROUP_ID = "groupId"
        val alice = HoldingIdentity("O=Alice, L=London, C=GB", GROUP_ID)
        val bob = HoldingIdentity("O=Bob, L=London, C=GB", GROUP_ID)
        val time = clock.instant()
        val testEntries = mapOf(
            EPOCH_KEY to "1",
            MPV_KEY to "1",
            MODIFIED_TIME_KEY to time.toString()
        )
        val updatedTestEntries = mapOf(
            EPOCH_KEY to "2",
            MPV_KEY to "2",
            MODIFIED_TIME_KEY to clock.instant().toString()
        )
        val aliceAvroGroupParams: GroupParametersAvro = mock {
            on { viewOwner } doReturn alice
            on { groupParameters } doReturn convertToKeyValuePairList(testEntries)
        }
        val bobAvroGroupParams: GroupParametersAvro = mock {
            on { viewOwner } doReturn bob
            on { groupParameters } doReturn convertToKeyValuePairList(testEntries)
        }
        val updatedBobAvroGroupParams: GroupParametersAvro = mock {
            on { viewOwner } doReturn bob
            on { groupParameters } doReturn convertToKeyValuePairList(updatedTestEntries)
        }

        val groupParams: GroupParameters = mock {
            on { entries } doReturn testEntries.entries
        }
        val updatedGroupParams: GroupParameters = mock {
            on { entries } doReturn updatedTestEntries.entries
        }

        private fun convertToKeyValuePairList(data: Map<String, String>) = KeyValuePairList(
            data.entries.map {
                KeyValuePair(it.key, it.value)
            }
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
        assertThat(groupParametersCache.get(alice.toCorda())?.entries).isEqualTo(groupParams.entries)
    }

    @Test
    fun `group params cache is successfully updated with new record`() {
        groupParametersProcessor.onSnapshot(
            mapOf(
                alice.toCorda().shortHash.value to aliceAvroGroupParams
            )
        )
        // processing new record
        groupParametersProcessor.onNext(
            Record("topic", bob.toCorda().shortHash.value, bobAvroGroupParams),
            null,
            mapOf(
                alice.toCorda().shortHash.value to aliceAvroGroupParams
            )
        )
        with(groupParametersCache.getAll()) {
            assertThat(this.keys).containsExactlyInAnyOrder(alice.toCorda(), bob.toCorda())
            assertThat(this.get(alice.toCorda())).isEqualTo(groupParams)
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
        // processing update for Bob
        groupParametersProcessor.onNext(
            Record("topic", bob.toCorda().shortHash.value, updatedBobAvroGroupParams),
            bobAvroGroupParams,
            mapOf(
                alice.toCorda().shortHash.value to aliceAvroGroupParams,
                bob.toCorda().shortHash.value to bobAvroGroupParams
            )
        )
        with(groupParametersCache.getAll()) {
            assertThat(this.keys).containsExactlyInAnyOrder(alice.toCorda(), bob.toCorda())
            assertThat(this.values).containsExactlyInAnyOrder(groupParams, updatedGroupParams)
        }
    }
}