package net.corda.messaging.emulation.topic.model

import net.corda.messaging.api.records.Record
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.net.URL

class RecordMetadataTest {
    @Test
    fun `castToType returns null if the key has the wrong type`() {
        val record = RecordMetadata(1L, Record("topic", "key", URL("https://www.corda.net/")), 4)

        val casted = record.castToType(URL::class.java, URL::class.java)

        assertThat(casted).isNull()
    }

    @Test
    fun `castToType returns null if the value has the wrong type`() {
        val record = RecordMetadata(1L, Record("topic", "key", URL("https://www.corda.net/")), 4)

        val casted = record.castToType(String::class.java, String::class.java)

        assertThat(casted).isNull()
    }

    @Test
    fun `castToType returns the record if the value and key are correct`() {
        val record = RecordMetadata(1L, Record("topic", "key", URL("https://www.corda.net/")), 4)

        val casted = record.castToType(String::class.java, URL::class.java)

        assertThat(casted).isNotNull
    }

    @Test
    fun `castToType returns the record if the value is null and the key is correct`() {
        val record = RecordMetadata(1L, Record("topic", "key", null), 4)

        val casted = record.castToType(String::class.java, URL::class.java)

        assertThat(casted).isNotNull
    }
}
