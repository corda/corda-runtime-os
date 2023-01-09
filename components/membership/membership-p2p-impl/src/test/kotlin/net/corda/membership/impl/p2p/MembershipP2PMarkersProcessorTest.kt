package net.corda.membership.impl.p2p

import net.corda.data.membership.command.registration.RegistrationCommand
import net.corda.data.membership.command.registration.mgm.DeclineRegistration
import net.corda.membership.p2p.helpers.TtlIdsFactory
import net.corda.messaging.api.records.Record
import net.corda.data.p2p.markers.AppMessageMarker
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock

class MembershipP2PMarkersProcessorTest {
    private val recordWithKey = mock<Record<String, AppMessageMarker>>()
    private val recordWithoutKey = mock<Record<String, AppMessageMarker>>()
    private val key = "key"
    private val ttlIdsFactory = mock<TtlIdsFactory> {
        on { extractKey(recordWithKey) } doReturn key
        on { extractKey(recordWithoutKey) } doReturn null
    }

    private val processor = MembershipP2PMarkersProcessor(ttlIdsFactory)

    @Test
    fun `onNext returns decline for the records that has keys (and only for those)`() {
        val records = processor.onNext(
            listOf(
                recordWithKey,
                recordWithoutKey,
            )
        )

        assertThat(records)
            .hasSize(1)
            .allSatisfy { record ->
                val value = record.value as? RegistrationCommand
                assertThat(value?.command).isInstanceOf(DeclineRegistration::class.java)
            }
            .allSatisfy { record ->
                assertThat(record.key).isEqualTo(key)
            }
    }
}
