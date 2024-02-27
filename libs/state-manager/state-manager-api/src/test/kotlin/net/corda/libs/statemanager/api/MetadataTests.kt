package net.corda.libs.statemanager.api

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatCode
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import java.util.stream.Stream

class MetadataTests {
    companion object {
        @JvmStatic
        private fun acceptedTypes(): Stream<Any> =
            Stream.of(
                "foo",
                123,
                true
            )
    }

    @ParameterizedTest
    @MethodSource("acceptedTypes")
    fun `accept primitive types`(value: Any) {
        assertThatCode {
            Metadata(mapOf("foo" to value))
        }.doesNotThrowAnyException()
    }

    @Test
    fun `fail all non-primitive types`() {
        val list = listOf("Na Na Na Na Na Na Na Na", "Batman")
        assertThatThrownBy { Metadata(mapOf("joker" to Superman(1000), "batman" to list)) }
            .isExactlyInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContainingAll("joker", "batman", Superman::class.java.name, list.javaClass.name)
    }

    @Test
    fun `equals works as expected with map`() {
        val meta1 = Metadata(mapOf("foo" to "bar"))
        val meta2 = Metadata(mapOf("foo" to "bar"))
        assertThat(meta2).isEqualTo(meta1)
        assertThat(meta2).isNotSameAs(meta1)
    }

    @Test
    fun `new meta with additional elements`() {
        val meta1 = Metadata(mapOf("foo" to "bar"))
        assertThat(meta1.plus("batman" to "joker"))
            .containsExactlyInAnyOrderEntriesOf(mapOf("foo" to "bar", "batman" to "joker"))
    }

    @Test
    fun `contains key with value returns true when key and value match`() {
        val meta1 = Metadata(mapOf("foo" to "bar"))
        assertThat(meta1.containsKeyWithValue("foo", "bar")).isTrue()
    }

    @Test
    fun `contains key with value returns false when key matches but value does not`() {
        val meta1 = Metadata(mapOf("foo" to true))
        assertThat(meta1.containsKeyWithValue("foo", false)).isFalse()
    }

    data class Superman(val kudos: Int)
}
