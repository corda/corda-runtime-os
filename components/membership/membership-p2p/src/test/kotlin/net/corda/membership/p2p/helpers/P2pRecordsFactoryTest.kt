package net.corda.membership.p2p.helpers

import net.corda.data.CordaAvroSerializationFactory
import net.corda.data.CordaAvroSerializer
import net.corda.data.KeyValuePairList
import net.corda.data.identity.HoldingIdentity
import net.corda.libs.configuration.SmartConfig
import net.corda.membership.p2p.helpers.P2pRecordsFactory.Companion.MEMBERSHIP_P2P_SUBSYSTEM
import net.corda.membership.p2p.helpers.P2pRecordsFactory.Companion.getTtlMinutes
import net.corda.data.p2p.app.AuthenticatedMessage
import net.corda.schema.Schemas.P2P.Companion.P2P_OUT_TOPIC
import net.corda.schema.configuration.MembershipConfig.TtlsConfig.TTLS
import net.corda.test.util.time.TestClock
import net.corda.v5.base.exceptions.CordaRuntimeException
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.SoftAssertions.assertSoftly
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.time.Instant

class P2pRecordsFactoryTest {
    private val serializer = mock<CordaAvroSerializer<KeyValuePairList>>()
    private val cordaAvroSerializationFactory = mock<CordaAvroSerializationFactory> {
        on { createAvroSerializer<KeyValuePairList>(any()) } doReturn serializer
    }
    private val clock = TestClock(Instant.ofEpochMilli(2000))

    private val factory = P2pRecordsFactory(
        cordaAvroSerializationFactory, clock
    )

    private val holdingIdentity1 = HoldingIdentity(
        "name1", "group"
    )

    private val holdingIdentity2 = HoldingIdentity(
        "name2", "group"
    )

    private val dataBytes = "data".toByteArray()

    @Test
    fun `createRecords throws exception when serialize failed`() {
        val data = mock<KeyValuePairList>()
        whenever(serializer.serialize(any())).doReturn(null)

        assertThrows<CordaRuntimeException> {
            factory.createAuthenticatedMessageRecord(holdingIdentity1, holdingIdentity2, data)
        }
    }

    @Test
    fun `createRecords create the correct record`() {
        val data = mock<KeyValuePairList>()
        whenever(serializer.serialize(eq(data))).doReturn(dataBytes)

        val record = factory.createAuthenticatedMessageRecord(holdingIdentity1, holdingIdentity2, data, 3)

        assertSoftly {
            it.assertThat(record.topic).isEqualTo(P2P_OUT_TOPIC)
            it.assertThat(record.key).isEqualTo(
                "Membership: {\"x500Name\": \"name1\", \"groupId\": \"group\"} -> {\"x500Name\": \"name2\", \"groupId\": \"group\"}"
            )
            it.assertThat(record.value?.message).isInstanceOf(AuthenticatedMessage::class.java)
            val value = record.value?.message as? AuthenticatedMessage
            it.assertThat(value?.payload?.array()).isEqualTo(dataBytes)
            val header = value?.header
            it.assertThat(header?.destination).isEqualTo(holdingIdentity2)
            it.assertThat(header?.source).isEqualTo(holdingIdentity1)
            it.assertThat(header?.ttl).isEqualTo(
                Instant.ofEpochMilli(2000 + 3 * 60 * 1000)
            )
            it.assertThat(header?.subsystem).isEqualTo(
                MEMBERSHIP_P2P_SUBSYSTEM
            )
        }
    }

    @Test
    fun `createRecords creates messages with null TTL when no waiting period was defined`() {
        val data = mock<KeyValuePairList>()
        whenever(serializer.serialize(eq(data))).doReturn(dataBytes)

        val record = factory.createAuthenticatedMessageRecord(holdingIdentity1, holdingIdentity2, data)

        val value = record.value?.message as? AuthenticatedMessage
        val header = value?.header
        assertThat(header?.ttl).isNull()
    }

    @Test
    fun `createRecords with explicit ID use the ID`() {
        val id = "Test-ID"
        val data = mock<KeyValuePairList>()
        whenever(serializer.serialize(eq(data))).doReturn(dataBytes)

        val record = factory.createAuthenticatedMessageRecord(
            holdingIdentity1,
            holdingIdentity2,
            data,
            id = id
        )

        val value = record.value?.message as? AuthenticatedMessage
        val header = value?.header
        assertThat(header?.messageId).isEqualTo(id)
    }

    @Nested
    inner class GetTtlMinutesTests {
        private val config = mock<SmartConfig> {
            on { getIsNull("$TTLS.null") } doReturn true
            on { getIsNull("$TTLS.hasValue") } doReturn false
            on { getLong("$TTLS.hasValue") } doReturn 22
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
