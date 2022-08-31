package net.corda.common.json.serializers

import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.core.JsonParseException
import com.fasterxml.jackson.core.JsonParser
import net.corda.v5.base.types.MemberX500Name
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify

class StandardTypesModuleTest {
    @Nested
    inner class MemberX500NameDeserializerTest {
        @Test
        fun `deserialize parse the text`() {
            val parser = mock<JsonParser> {
                on { text } doReturn "O=Alice, L=London, C=GB"
            }

            val name = MemberX500NameDeserializer.deserialize(parser, mock())

            assertThat(name.organisation).isEqualTo("Alice")
        }

        @Test
        fun `deserialize throw exception in case of a failure`() {
            val parser = mock<JsonParser> {
                on { text } doReturn "Invalid X500"
            }

            assertThrows<JsonParseException> {
                MemberX500NameDeserializer.deserialize(parser, mock())
            }
        }
    }

    @Nested
    inner class MemberX500NameSerializerTest {
        @Test
        fun `serialize write the member string`() {
            val value = MemberX500Name.parse("O=Bob, L=London, C=GB")
            val gen = mock<JsonGenerator>()

            MemberX500NameSerializer.serialize(value, gen, mock())

            verify(gen).writeString("O=Bob, L=London, C=GB")
        }
    }
}
