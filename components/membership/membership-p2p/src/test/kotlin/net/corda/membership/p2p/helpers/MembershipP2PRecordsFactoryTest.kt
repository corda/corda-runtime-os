package net.corda.membership.p2p.helpers

import net.corda.avro.serialization.CordaAvroSerializationFactory
import net.corda.avro.serialization.CordaAvroSerializer
import net.corda.data.KeyValuePairList
import net.corda.data.identity.HoldingIdentity
import net.corda.data.p2p.app.MembershipStatusFilter
import net.corda.libs.configuration.SmartConfig
import net.corda.membership.p2p.helpers.MembershipP2pRecordsFactory.Companion.MEMBERSHIP_P2P_SUBSYSTEM
import net.corda.membership.p2p.helpers.MembershipP2pRecordsFactory.Companion.getTtlMinutes
import net.corda.p2p.messaging.P2pRecordsFactory
import net.corda.schema.configuration.MembershipConfig.TtlsConfig.TTLS
import net.corda.v5.base.exceptions.CordaRuntimeException
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class MembershipP2PRecordsFactoryTest {
    private val serializer = mock<CordaAvroSerializer<KeyValuePairList>>()
    private val cordaAvroSerializationFactory = mock<CordaAvroSerializationFactory> {
        on { createAvroSerializer<KeyValuePairList>(any()) } doReturn serializer
    }
    private val p2pRecordsFactory = mock<P2pRecordsFactory>()

    private val factory = MembershipP2pRecordsFactory(
        cordaAvroSerializationFactory,
        p2pRecordsFactory,
    )

    private val holdingIdentity1 = HoldingIdentity(
        "name1",
        "group"
    )

    private val holdingIdentity2 = HoldingIdentity(
        "name2",
        "group"
    )

    private val dataBytes = "data".toByteArray()

    @Test
    fun `createRecords throws exception when serialize failed`() {
        val data = mock<KeyValuePairList>()
        whenever(serializer.serialize(any())).doReturn(null)

        assertThrows<CordaRuntimeException> {
            factory.createAuthenticatedMessageRecord(holdingIdentity1, holdingIdentity2, data)
        }
        verify(p2pRecordsFactory, never())
            .createAuthenticatedMessageRecord(any(), any(), any(), any(), any(), any(), any(), any())
    }

    @Test
    fun `createRecords create the correct record with default filter`() {
        val data = mock<KeyValuePairList>()
        whenever(serializer.serialize(eq(data))).doReturn(dataBytes)
        val minutesToWait = 3L

        factory.createAuthenticatedMessageRecord(holdingIdentity1, holdingIdentity2, data, minutesToWait)
        verify(p2pRecordsFactory).createAuthenticatedMessageRecord(
            eq(holdingIdentity1),
            eq(holdingIdentity2),
            eq(dataBytes),
            eq(MEMBERSHIP_P2P_SUBSYSTEM),
            eq("Membership: {\"x500Name\": \"name1\", \"groupId\": \"group\"} -> {\"x500Name\": \"name2\", \"groupId\": \"group\"}"),
            eq(minutesToWait),
            any(),
            eq(MembershipStatusFilter.ACTIVE),
        )
    }

    @Test
    fun `createRecords create the correct record with pending filter`() {
        val data = mock<KeyValuePairList>()
        whenever(serializer.serialize(eq(data))).doReturn(dataBytes)
        val minutesToWait = 3L

        factory.createAuthenticatedMessageRecord(
            source = holdingIdentity1,
            destination = holdingIdentity2,
            content = data,
            minutesToWait = minutesToWait,
            filter = MembershipStatusFilter.PENDING
        )

        verify(p2pRecordsFactory).createAuthenticatedMessageRecord(
            eq(holdingIdentity1),
            eq(holdingIdentity2),
            eq(dataBytes),
            eq(MEMBERSHIP_P2P_SUBSYSTEM),
            eq("Membership: {\"x500Name\": \"name1\", \"groupId\": \"group\"} -> {\"x500Name\": \"name2\", \"groupId\": \"group\"}"),
            eq(minutesToWait),
            any(),
            eq(MembershipStatusFilter.PENDING),
        )
    }

    @Test
    fun `createRecords creates messages with null TTL when no waiting period was defined`() {
        val data = mock<KeyValuePairList>()
        whenever(serializer.serialize(eq(data))).doReturn(dataBytes)

        factory.createAuthenticatedMessageRecord(holdingIdentity1, holdingIdentity2, data)
        verify(p2pRecordsFactory).createAuthenticatedMessageRecord(
            eq(holdingIdentity1),
            eq(holdingIdentity2),
            eq(dataBytes),
            eq(MEMBERSHIP_P2P_SUBSYSTEM),
            eq("Membership: {\"x500Name\": \"name1\", \"groupId\": \"group\"} -> {\"x500Name\": \"name2\", \"groupId\": \"group\"}"),
            eq(null),
            any(),
            eq(MembershipStatusFilter.ACTIVE),
        )
    }

    @Test
    fun `createRecords with explicit ID use the ID`() {
        val id = "Test-ID"
        val data = mock<KeyValuePairList>()
        whenever(serializer.serialize(eq(data))).doReturn(dataBytes)

        factory.createAuthenticatedMessageRecord(
            holdingIdentity1,
            holdingIdentity2,
            data,
            id = id
        )
        verify(p2pRecordsFactory).createAuthenticatedMessageRecord(
            eq(holdingIdentity1),
            eq(holdingIdentity2),
            eq(dataBytes),
            eq(MEMBERSHIP_P2P_SUBSYSTEM),
            eq("Membership: {\"x500Name\": \"name1\", \"groupId\": \"group\"} -> {\"x500Name\": \"name2\", \"groupId\": \"group\"}"),
            eq(null),
            eq(id),
            eq(MembershipStatusFilter.ACTIVE),
        )
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
