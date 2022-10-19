package net.corda.common.json.serializers

import net.corda.v5.application.marshalling.json.JsonNodeReader
import net.corda.v5.application.marshalling.json.JsonWriter
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.v5.base.types.MemberX500Name
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify

class MemberX500NameSerializationTest {
    @Nested
    inner class MemberX500NameDeserializerTest {
        @Test
        fun `deserialize parse the text`() {
            val jsonNodeReader = mock<JsonNodeReader> {
                on { asText() } doReturn "O=Alice, L=London, C=GB"
            }

            val name = MemberX500NameDeserializer().deserialize(jsonNodeReader)

            assertThat(name.organization).isEqualTo("Alice")
        }

        @Test
        fun `deserialize throw exception in case of a failure`() {
            val jsonNodeReader = mock<JsonNodeReader> {
                on { asText() } doReturn "Invalid X500"
            }

            assertThrows<CordaRuntimeException> {
                MemberX500NameDeserializer().deserialize(jsonNodeReader)
            }
        }
    }

    @Nested
    inner class MemberX500NameSerializerTest {
        @Test
        fun `serialize write the member string`() {
            val value = MemberX500Name.parse("O=Bob, L=London, C=GB")
            val jsonWriter = mock<JsonWriter>()

            MemberX500NameSerializer().serialize(value, jsonWriter)

            verify(jsonWriter).writeString("O=Bob, L=London, C=GB")
        }
    }
}
