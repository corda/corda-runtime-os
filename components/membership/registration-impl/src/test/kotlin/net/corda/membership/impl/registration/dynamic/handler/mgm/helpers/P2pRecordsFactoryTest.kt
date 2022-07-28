package net.corda.membership.impl.registration.dynamic.mgm.handler.helpers

import net.corda.data.CordaAvroSerializationFactory
import net.corda.data.CordaAvroSerializer
import net.corda.data.identity.HoldingIdentity
import net.corda.membership.impl.registration.dynamic.mgm.handler.helpers.P2pRecordsFactory.Companion.MEMBERSHIP_P2P_SUBSYSTEM
import net.corda.p2p.app.AuthenticatedMessage
import net.corda.schema.Schemas.P2P.Companion.P2P_OUT_TOPIC
import net.corda.test.util.time.TestClock
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.v5.base.types.LayeredPropertyMap
import org.assertj.core.api.SoftAssertions.assertSoftly
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.time.Instant

class P2pRecordsFactoryTest {
    private val serializer = mock<CordaAvroSerializer<LayeredPropertyMap>>()
    private val cordaAvroSerializationFactory = mock<CordaAvroSerializationFactory> {
        on { createAvroSerializer<LayeredPropertyMap>(any()) } doReturn serializer
    }
    private val clock = TestClock(Instant.ofEpochMilli(2000))

    private val factory = P2pRecordsFactory(
        cordaAvroSerializationFactory, clock
    )

    @Test
    fun `createRecords throws exception when serialize failed`() {
        val data = mock<LayeredPropertyMap>()
        whenever(serializer.serialize(any())).doReturn(null)

        assertThrows<CordaRuntimeException> {
            factory.createAuthenticatedMessageRecord(
                HoldingIdentity(
                    "name1", "group"
                ),
                HoldingIdentity(
                    "name2", "group"
                ),
                data
            )
        }
    }

    @Test
    fun `createRecords create the correct record`() {
        val data = mock<LayeredPropertyMap>()
        whenever(serializer.serialize(eq(data))).doReturn("data".toByteArray())

        val record = factory.createAuthenticatedMessageRecord(
            HoldingIdentity(
                "name1", "group"
            ),
            HoldingIdentity(
                "name2", "group"
            ),
            data
        )

        assertSoftly {
            it.assertThat(record.topic).isEqualTo(P2P_OUT_TOPIC)
            it.assertThat(record.key).isEqualTo(
                "Membership: {\"x500Name\": \"name1\", \"groupId\": \"group\"} -> {\"x500Name\": \"name2\", \"groupId\": \"group\"}"
            )
            it.assertThat(record.value?.message).isInstanceOf(AuthenticatedMessage::class.java)
            val value = record.value?.message as? AuthenticatedMessage
            it.assertThat(value?.payload?.array()).isEqualTo("data".toByteArray())
            val header = value?.header
            it.assertThat(header?.destination).isEqualTo(
                HoldingIdentity(
                    "name2", "group"
                )
            )
            it.assertThat(header?.source).isEqualTo(
                HoldingIdentity(
                    "name1", "group"
                )
            )
            it.assertThat(header?.ttl).isEqualTo(
                Instant.ofEpochMilli(2000 + 5 * 60 * 1000)
            )
            it.assertThat(header?.subsystem).isEqualTo(
                MEMBERSHIP_P2P_SUBSYSTEM
            )
        }
    }
}
