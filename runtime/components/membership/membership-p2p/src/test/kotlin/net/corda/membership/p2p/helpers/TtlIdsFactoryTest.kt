package net.corda.membership.p2p.helpers

import net.corda.messaging.api.records.Record
import net.corda.data.p2p.markers.AppMessageMarker
import net.corda.data.p2p.markers.GatewaySentMarker
import net.corda.data.p2p.markers.TtlExpiredMarker
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock

class TtlIdsFactoryTest {
    private val factory = TtlIdsFactory()

    @Test
    fun `extractKey returns null if the record key have the wrong prefix`() {
        val theKey = "a key"
        val record = mock<Record<String, AppMessageMarker>> {
            on { key } doReturn theKey
        }

        val key = factory.extractKey(record)

        assertThat(key).isNull()
    }

    @Test
    fun `extractKey returns null if the record value is null`() {
        val id = factory.createId("key")
        val record = mock<Record<String, AppMessageMarker>> {
            on { key } doReturn id
            on { value } doReturn null
        }

        val key = factory.extractKey(record)

        assertThat(key).isNull()
    }

    @Test
    fun `extractKey returns null if the record value is not TTL expire`() {
        val id = factory.createId("key")
        val theMarker = mock<GatewaySentMarker>()
        val theValue = mock<AppMessageMarker> {
            on { marker } doReturn theMarker
        }
        val record = mock<Record<String, AppMessageMarker>> {
            on { key } doReturn id
            on { value } doReturn theValue
        }

        val key = factory.extractKey(record)

        assertThat(key).isNull()
    }

    @Test
    fun `extractKey returns the correct key for TTL expire marker`() {
        val id = factory.createId("key-one")
        val theMarker = mock<TtlExpiredMarker>()
        val theValue = mock<AppMessageMarker> {
            on { marker } doReturn theMarker
        }
        val record = mock<Record<String, AppMessageMarker>> {
            on { key } doReturn id
            on { value } doReturn theValue
        }

        val key = factory.extractKey(record)

        assertThat(key).isEqualTo("key-one")
    }
}
