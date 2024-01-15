package net.corda.membership.impl.read.subscription

import net.corda.data.identity.HoldingIdentity
import net.corda.data.membership.PersistentGroupParameters
import net.corda.membership.impl.read.cache.MemberDataCache
import net.corda.membership.lib.GroupParametersFactory
import net.corda.membership.lib.GroupParametersNotaryUpdater.Companion.EPOCH_KEY
import net.corda.membership.lib.GroupParametersNotaryUpdater.Companion.MODIFIED_TIME_KEY
import net.corda.membership.lib.InternalGroupParameters
import net.corda.membership.lib.SignedGroupParameters
import net.corda.membership.lib.exceptions.FailedGroupParametersDeserialization
import net.corda.messaging.api.records.Record
import net.corda.test.util.time.TestClock
import net.corda.virtualnode.toCorda
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.nio.ByteBuffer
import java.time.Instant
import net.corda.data.membership.SignedGroupParameters as AvroGroupParameters

class GroupParametersProcessorTest {
    private companion object {
        const val GROUP_ID = "groupId"
    }

    private lateinit var groupParametersCache: MemberDataCache<InternalGroupParameters>
    private lateinit var groupParametersProcessor: GroupParametersProcessor

    private val clock = TestClock(Instant.ofEpochSecond(100))
    private val alice = HoldingIdentity("O=Alice, L=London, C=GB", GROUP_ID)
    private val bob = HoldingIdentity("O=Bob, L=London, C=GB", GROUP_ID)
    private val time = clock.instant()
    private val testEntries = mapOf(
        EPOCH_KEY to "1",
        MODIFIED_TIME_KEY to time.toString()
    )
    private val updatedTestEntries = mapOf(
        EPOCH_KEY to "2",
        MODIFIED_TIME_KEY to clock.instant().toString()
    )

    private val signedGroupParametersBytes: ByteArray = "original-signed".toByteArray()
    private val updatedSignedGroupParametersBytes: ByteArray = "updated-signed".toByteArray()

    private val signedGroupParameters: AvroGroupParameters = mock {
        on { groupParameters } doReturn ByteBuffer.wrap(signedGroupParametersBytes)
    }
    private val updatedSignedGroupParameters: AvroGroupParameters = mock {
        on { groupParameters } doReturn ByteBuffer.wrap(updatedSignedGroupParametersBytes)
    }

    private val aliceAvroGroupParams: PersistentGroupParameters = mock {
        on { viewOwner } doReturn alice
        on { groupParameters } doReturn signedGroupParameters
    }
    private val bobAvroGroupParams: PersistentGroupParameters = mock {
        on { viewOwner } doReturn bob
        on { groupParameters } doReturn signedGroupParameters
    }
    private val updatedBobAvroGroupParams: PersistentGroupParameters = mock {
        on { viewOwner } doReturn bob
        on { groupParameters } doReturn updatedSignedGroupParameters
    }

    private val groupParams: SignedGroupParameters = mock {
        on { entries } doReturn testEntries.entries
    }
    private val updatedGroupParams: SignedGroupParameters = mock {
        on { entries } doReturn updatedTestEntries.entries
    }

    private val groupParametersFactory: GroupParametersFactory = mock {
        on { create(signedGroupParameters) } doReturn groupParams
        on { create(updatedSignedGroupParameters) } doReturn updatedGroupParams
    }

    @BeforeEach
    fun setUp() {
        groupParametersCache = MemberDataCache.Impl()
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
    fun `Failure to create group parameters from snapshot is caught so processor does not go down`() {
        whenever(groupParametersFactory.create(any<AvroGroupParameters>()))
            .doThrow(FailedGroupParametersDeserialization)
        assertDoesNotThrow {
            groupParametersProcessor.onSnapshot(mapOf(alice.toCorda().shortHash.value to aliceAvroGroupParams))
        }
        assertThat(groupParametersCache.get(alice.toCorda())).isNull()
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

    @Test
    fun `group params cache is not updated and exception is caught if failure to create group parameter during onNext`() {
        groupParametersProcessor.onSnapshot(
            mapOf(
                alice.toCorda().shortHash.value to aliceAvroGroupParams,
                bob.toCorda().shortHash.value to bobAvroGroupParams
            )
        )
        whenever(groupParametersFactory.create(any<AvroGroupParameters>()))
            .doThrow(FailedGroupParametersDeserialization)
        // processing update for Bob
        assertDoesNotThrow {
            groupParametersProcessor.onNext(
                Record("topic", bob.toCorda().shortHash.value, updatedBobAvroGroupParams),
                bobAvroGroupParams,
                mapOf(
                    alice.toCorda().shortHash.value to aliceAvroGroupParams,
                    bob.toCorda().shortHash.value to bobAvroGroupParams
                )
            )
        }
        // Check old value is still present
        with(groupParametersCache.getAll()) {
            assertThat(this.keys).containsExactlyInAnyOrder(alice.toCorda(), bob.toCorda())
            assertThat(this.values).containsExactlyInAnyOrder(groupParams, groupParams)
        }
    }
}
