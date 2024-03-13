package net.corda.p2.messaging

import net.corda.data.identity.HoldingIdentity
import net.corda.data.p2p.app.AuthenticatedMessage
import net.corda.data.p2p.app.MembershipStatusFilter
import net.corda.p2p.messaging.P2pRecordsFactory
import net.corda.schema.Schemas
import net.corda.test.util.time.TestClock
import org.assertj.core.api.SoftAssertions.assertSoftly
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.temporal.ChronoUnit

class P2pRecordsFactoryTest {
    private companion object {
        val SUBSYTEM = "membership"
        val KEY = "key"
        val MINUTES_TO_WAIT = 10L
        val ID = "id"
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
    private val data = byteArrayOf(1)
    private val factory = P2pRecordsFactory(
        clock
    )

    @Test
    fun `createRecords create the correct record with default filter`() {
        val record = factory.createAuthenticatedMessageRecord(
            holdingIdentity1,
            holdingIdentity2,
            data,
            SUBSYTEM,
            KEY,
            MINUTES_TO_WAIT,
            ID
        )
        assertSoftly {
            it.assertThat(record.key).isEqualTo(KEY)
            it.assertThat(record.topic).isEqualTo(Schemas.P2P.P2P_OUT_TOPIC)
            val value = record.value?.message as? AuthenticatedMessage
            val header = value?.header
            it.assertThat(header?.source).isEqualTo(holdingIdentity1)
            it.assertThat(header?.destination).isEqualTo(holdingIdentity2)
            it.assertThat(header?.subsystem).isEqualTo(SUBSYTEM)
            it.assertThat(header?.ttl).isBeforeOrEqualTo(clock.instant().plus(MINUTES_TO_WAIT, ChronoUnit.MINUTES))
            it.assertThat(header?.messageId).isEqualTo(ID)
            it.assertThat(header?.statusFilter).isEqualTo(MembershipStatusFilter.ACTIVE)
        }
    }

    @Test
    fun `createRecords create the correct record with pending filter`() {
        val record = factory.createAuthenticatedMessageRecord(
            holdingIdentity1,
            holdingIdentity2,
            data,
            SUBSYTEM,
            KEY,
            MINUTES_TO_WAIT,
            ID,
            MembershipStatusFilter.PENDING
        )
        assertSoftly {
            val value = record.value?.message as? AuthenticatedMessage
            it.assertThat(value?.header?.statusFilter).isEqualTo(MembershipStatusFilter.PENDING)
        }
    }

    @Test
    fun `createRecords create the correct record with default TTL`() {
        val record = factory.createAuthenticatedMessageRecord(
            holdingIdentity1,
            holdingIdentity2,
            data,
            SUBSYTEM,
            KEY,
            id = ID
        )
        assertSoftly {
            val value = record.value?.message as? AuthenticatedMessage
            it.assertThat(value?.header?.ttl).isNull()
        }
    }

    @Test
    fun `createRecords create the correct record with default message ID`() {
        val record = factory.createAuthenticatedMessageRecord(
            holdingIdentity1,
            holdingIdentity2,
            data,
            SUBSYTEM,
            KEY,
            MINUTES_TO_WAIT
        )
        assertSoftly {
            val value = record.value?.message as? AuthenticatedMessage
            it.assertThat(value?.header?.messageId).isNotNull
        }
    }
}
