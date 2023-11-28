package net.corda.messaging.utils

import net.corda.messaging.api.records.Record
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class RecordExtensionsTest {

    @Test
    fun `toCordaProducerRecord maps all fields`() {
        val record = Record(
            topic = "topic",
            key = "key",
            value = "value",
            headers = listOf("a" to "b")
        )

        val result = record.toCordaProducerRecord()

        assertThat(result.topic).isEqualTo(record.topic)
        assertThat(result.key).isEqualTo(record.key)
        assertThat(result.value).isEqualTo(record.value)
        assertThat(result.headers).isEqualTo(record.headers)
    }
}
