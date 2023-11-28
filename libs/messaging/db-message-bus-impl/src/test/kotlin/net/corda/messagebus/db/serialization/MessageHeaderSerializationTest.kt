package net.corda.messagebus.db.serialization

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class MessageHeaderSerializationTest {

    @Test
    fun `Serialize headers`() {
        val headers = listOf("item1" to "value1a")
        val result = MessageHeaderSerializerImpl().serialize(headers).trimIndent()
        assertThat(result).isEqualTo("{\"items\":[{\"key\":\"item1\",\"value\":\"value1a\"}]}")
    }

    @Test
    fun `Serialize empty length headers`() {
        val result = MessageHeaderSerializerImpl().serialize(listOf()).trimIndent()
        assertThat(result).isEqualTo("{\"items\":[]}")
    }

    @Test
    fun `deserialize headers`() {
        val json = """{
            "items":[
             {
                 "key":"item1",
                 "value":"value1a"
             },
             {
                 "key":"item1",
                 "value":"value1b"
             },
             {
                 "key":"item2",
                 "value":"value2a"
             }
        ]
}
        """.trimMargin()

        val result = MessageHeaderSerializerImpl().deserialize(json)

        assertThat(result[0].first).isEqualTo("item1")
        assertThat(result[0].second).isEqualTo("value1a")
        assertThat(result[1].first).isEqualTo("item1")
        assertThat(result[1].second).isEqualTo("value1b")
        assertThat(result[2].first).isEqualTo("item2")
        assertThat(result[2].second).isEqualTo("value2a")
    }

    @Test
    fun `deserialize empty`() {
        val json = "{}"

        val result = MessageHeaderSerializerImpl().deserialize(json)

        assertThat(result).isEmpty()
    }
}
