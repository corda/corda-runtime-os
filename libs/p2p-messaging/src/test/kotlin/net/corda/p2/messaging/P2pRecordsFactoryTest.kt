package net.corda.p2.messaging

import net.corda.avro.serialization.CordaAvroSerializationFactory
import net.corda.avro.serialization.CordaAvroSerializer
import net.corda.data.KeyValuePairList
import net.corda.data.identity.HoldingIdentity
import net.corda.data.p2p.app.AuthenticatedMessage
import net.corda.data.p2p.app.MembershipStatusFilter
import net.corda.p2p.messaging.P2pRecordsFactory
import net.corda.p2p.messaging.Subsystem
import net.corda.schema.Schemas
import net.corda.test.util.time.TestClock
import net.corda.v5.base.exceptions.CordaRuntimeException
import org.assertj.core.api.SoftAssertions.assertSoftly
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.time.Instant
import java.time.temporal.ChronoUnit

class P2pRecordsFactoryTest {
    private companion object {
        val KEY = "key"
        val MINUTES_TO_WAIT = 10L
        val MESSAGE_ID = "id"
    }

    private val data = mock<KeyValuePairList>()
    private val serializedData = byteArrayOf(1)
    private val serializer = mock<CordaAvroSerializer<KeyValuePairList>> {
        on { serialize(eq(data)) } doReturn serializedData
    }
    private val cordaAvroSerializationFactory = mock<CordaAvroSerializationFactory> {
        on { createAvroSerializer<KeyValuePairList>(any()) } doReturn serializer
    }

    private val clock = TestClock(Instant.ofEpochMilli(2000))
    private val holdingIdentity1 = HoldingIdentity(
        "name1",
        "group"
    )
    private val holdingIdentity2 = HoldingIdentity(
        "name2",
        "group"
    )
    private val factory = P2pRecordsFactory(
        clock,
        cordaAvroSerializationFactory,
    )

    @Test
    fun `createRecords throws exception when serialize failed`() {
        val data = mock<KeyValuePairList>()
        whenever(serializer.serialize(any())).doReturn(null)

        assertThrows<CordaRuntimeException> {
            factory.createAuthenticatedMessageRecord(
                holdingIdentity1,
                holdingIdentity2,
                data,
                Subsystem.LINK_MANAGER,
                KEY,
                MINUTES_TO_WAIT,
            )
        }
    }

    @Test
    fun `createRecords creates the correct record with default filter`() {
        val record = factory.createAuthenticatedMessageRecord(
            holdingIdentity1,
            holdingIdentity2,
            data,
            Subsystem.LINK_MANAGER,
            KEY,
            MINUTES_TO_WAIT,
            MESSAGE_ID
        )
        assertSoftly {
            it.assertThat(record.key).isEqualTo(KEY)
            it.assertThat(record.topic).isEqualTo(Schemas.P2P.P2P_OUT_TOPIC)
            val value = record.value?.message as? AuthenticatedMessage
            val header = value?.header
            it.assertThat(header?.source).isEqualTo(holdingIdentity1)
            it.assertThat(header?.destination).isEqualTo(holdingIdentity2)
            it.assertThat(header?.subsystem).isEqualTo(Subsystem.LINK_MANAGER.systemName)
            it.assertThat(header?.ttl).isBeforeOrEqualTo(clock.instant().plus(MINUTES_TO_WAIT, ChronoUnit.MINUTES))
            it.assertThat(header?.messageId).startsWith(MESSAGE_ID)
            it.assertThat(header?.statusFilter).isEqualTo(MembershipStatusFilter.ACTIVE)
        }
    }

    @Test
    fun `createRecords creates the correct record with pending filter`() {
        val record = factory.createAuthenticatedMessageRecord(
            holdingIdentity1,
            holdingIdentity2,
            data,
            Subsystem.LINK_MANAGER,
            KEY,
            MINUTES_TO_WAIT,
            filter = MembershipStatusFilter.PENDING
        )
        assertSoftly {
            val value = record.value?.message as? AuthenticatedMessage
            it.assertThat(value?.header?.statusFilter).isEqualTo(MembershipStatusFilter.PENDING)
        }
    }

    @Test
    fun `createRecords creates the correct record with default TTL`() {
        val record = factory.createAuthenticatedMessageRecord(
            holdingIdentity1,
            holdingIdentity2,
            data,
            Subsystem.LINK_MANAGER,
            KEY,
        )
        assertSoftly {
            val value = record.value?.message as? AuthenticatedMessage
            it.assertThat(value?.header?.ttl).isNull()
        }
    }

    @Test
    fun `createRecords creates the correct record with default topic key`() {
        val record = factory.createAuthenticatedMessageRecord(
            holdingIdentity1,
            holdingIdentity2,
            data,
            Subsystem.LINK_MANAGER,
        )
        assertSoftly {
            it.assertThat(record.key).isNotNull
        }
    }

    @Test
    fun `createRecords create the correct record with default message ID`() {
        val record = factory.createAuthenticatedMessageRecord(
            holdingIdentity1,
            holdingIdentity2,
            data,
            Subsystem.LINK_MANAGER,
            KEY,
            MINUTES_TO_WAIT
        )
        assertSoftly {
            val value = record.value?.message as? AuthenticatedMessage
            it.assertThat(value?.header?.messageId).isNotNull
        }
    }
}
