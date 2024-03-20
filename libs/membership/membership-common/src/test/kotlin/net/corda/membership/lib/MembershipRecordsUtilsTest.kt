package net.corda.membership.lib

import net.corda.avro.serialization.CordaAvroSerializationFactory
import net.corda.avro.serialization.CordaAvroSerializer
import net.corda.data.KeyValuePairList
import net.corda.data.identity.HoldingIdentity
import net.corda.data.p2p.app.AuthenticatedMessage
import net.corda.data.p2p.app.MembershipStatusFilter
import net.corda.libs.configuration.SmartConfig
import net.corda.p2p.messaging.P2pRecordsFactory
import net.corda.schema.Schemas
import net.corda.schema.configuration.MembershipConfig
import net.corda.test.util.time.TestClock
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.SoftAssertions
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import java.time.Instant
import java.time.temporal.ChronoUnit

class MembershipRecordsUtilsTest {
    private companion object {
        val MINUTES_TO_WAIT = 10L
        val MESSAGE_ID = "id"
    }

    @Nested
    inner class CreateRecordsTests {
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
        fun `createMembershipRecords creates the correct record with default filter`() {
            val record = factory.createMembershipAuthenticatedMessageRecord(
                holdingIdentity1,
                holdingIdentity2,
                data,
                MINUTES_TO_WAIT,
                MESSAGE_ID,
            )
            SoftAssertions.assertSoftly {
                it.assertThat(record.key).isEqualTo("Membership: $holdingIdentity1 -> $holdingIdentity2")
                it.assertThat(record.topic).isEqualTo(Schemas.P2P.P2P_OUT_TOPIC)
                val value = record.value?.message as? AuthenticatedMessage
                val header = value?.header
                it.assertThat(header?.source).isEqualTo(holdingIdentity1)
                it.assertThat(header?.destination).isEqualTo(holdingIdentity2)
                it.assertThat(header?.subsystem).isEqualTo(MEMBERSHIP_P2P_SUBSYSTEM)
                it.assertThat(header?.ttl).isBeforeOrEqualTo(clock.instant().plus(MINUTES_TO_WAIT, ChronoUnit.MINUTES))
                it.assertThat(header?.messageId).isEqualTo(MESSAGE_ID)
                it.assertThat(header?.statusFilter).isEqualTo(MembershipStatusFilter.ACTIVE)
            }
        }

        @Test
        fun `createMembershipRecords creates the correct record with default TTL`() {
            val record = factory.createMembershipAuthenticatedMessageRecord(
                holdingIdentity1,
                holdingIdentity2,
                data,
            )
            SoftAssertions.assertSoftly {
                val value = record.value?.message as? AuthenticatedMessage
                it.assertThat(value?.header?.ttl).isNull()
            }
        }

        @Test
        fun `createMembershipRecords creates the correct record with pending filter`() {
            val record = factory.createMembershipAuthenticatedMessageRecord(
                holdingIdentity1,
                holdingIdentity2,
                data,
                filter = MembershipStatusFilter.PENDING,
            )
            SoftAssertions.assertSoftly {
                val value = record.value?.message as? AuthenticatedMessage
                it.assertThat(value?.header?.statusFilter).isEqualTo(MembershipStatusFilter.PENDING)
            }
        }
    }

    @Nested
    inner class GetTtlMinutesTests {
        private val config = mock<SmartConfig> {
            on { getIsNull("${MembershipConfig.TtlsConfig.TTLS}.null") } doReturn true
            on { getIsNull("${MembershipConfig.TtlsConfig.TTLS}.hasValue") } doReturn false
            on { getLong("${MembershipConfig.TtlsConfig.TTLS}.hasValue") } doReturn 22
        }

        @Test
        fun `null name returns null`() {
            assertThat(config.getTtlMinutes(null)).isNull()
        }

        @Test
        fun `null value returns null`() {
            assertThat(config.getTtlMinutes("null")).isNull()
        }

        @Test
        fun `non null value returns value`() {
            assertThat(config.getTtlMinutes("hasValue")).isEqualTo(22)
        }
    }
}