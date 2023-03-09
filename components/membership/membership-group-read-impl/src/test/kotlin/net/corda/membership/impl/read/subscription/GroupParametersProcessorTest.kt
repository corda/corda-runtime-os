package net.corda.membership.impl.read.subscription

import net.corda.data.KeyValuePair
import net.corda.data.KeyValuePairList
import net.corda.data.identity.HoldingIdentity
import net.corda.data.membership.PersistentGroupParameters
import net.corda.data.membership.SignedGroupParameters as AvroGroupParameters
import net.corda.membership.impl.read.cache.MemberDataCache
import net.corda.membership.lib.EPOCH_KEY
import net.corda.membership.lib.GroupParametersFactory
import net.corda.membership.lib.InternalGroupParameters
import net.corda.membership.lib.MODIFIED_TIME_KEY
import net.corda.membership.lib.MPV_KEY
import net.corda.membership.lib.SignedGroupParameters
import net.corda.messaging.api.records.Record
import net.corda.test.util.time.TestClock
import net.corda.virtualnode.toCorda
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import java.nio.ByteBuffer
import java.time.Instant

class GroupParametersProcessorTest {
    private companion object {
        val groupParametersCache = MemberDataCache.Impl<InternalGroupParameters>()
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

        val testEntriesList = convertToKeyValuePairList(testEntries)
        val updatedTestEntriesList = convertToKeyValuePairList(updatedTestEntries)

        val signedGroupParametersBytes: ByteArray = "original-signed".toByteArray()
        val updatedSignedGroupParametersBytes: ByteArray = "updated-signed".toByteArray()

        val signedGroupParameters: AvroGroupParameters = mock {
            on { groupParameters } doReturn ByteBuffer.wrap(signedGroupParametersBytes)
        }
        val updatedSignedGroupParameters: AvroGroupParameters = mock {
            on { groupParameters } doReturn ByteBuffer.wrap(updatedSignedGroupParametersBytes)
        }

        val aliceAvroGroupParams: PersistentGroupParameters = mock {
            on { viewOwner } doReturn alice
            on { groupParameters } doReturn signedGroupParameters
        }
        val bobAvroGroupParams: PersistentGroupParameters = mock {
            on { viewOwner } doReturn bob
            on { groupParameters } doReturn signedGroupParameters
        }
        val updatedBobAvroGroupParams: PersistentGroupParameters = mock {
            on { viewOwner } doReturn bob
            on { groupParameters } doReturn updatedSignedGroupParameters
        }

        val groupParams: SignedGroupParameters = mock {
            on { entries } doReturn testEntries.entries
        }
        val updatedGroupParams: SignedGroupParameters = mock {
            on { entries } doReturn updatedTestEntries.entries
        }

        val groupParametersFactory: GroupParametersFactory = mock {
            on { create(signedGroupParameters) } doReturn groupParams
            on { create(updatedSignedGroupParameters) } doReturn updatedGroupParams
        }

        private fun convertToKeyValuePairList(data: Map<String, String>) = KeyValuePairList(
            data.entries.map {
                KeyValuePair(it.key, it.value)
            }
        )
    }

    @BeforeEach
    fun setUp() {
        groupParametersProcessor = GroupParametersProcessor(groupParametersCache, groupParametersFactory)
    }

    @Test
    fun `key class is String`() {
        assertThat(groupParametersProcessor.keyClass).isEqualTo(String::class.java)
    }

    @Test
    fun `value class is GroupParameters`() {
        assertThat(groupParametersProcessor.valueClass).isEqualTo(PersistentGroupParameters::class.java)
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