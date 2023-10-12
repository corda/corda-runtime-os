package net.corda.libs.statemanager.impl

import com.fasterxml.jackson.databind.ObjectMapper
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.entry
import org.junit.jupiter.api.Test

class ObjectMapperExtTest {

    @Test
    fun convertToMetadataWorksCorrectlyForEmptyJson() {
        val metadataJson = "{}"

        val metadata = ObjectMapper().convertToMetadata(metadataJson)
        assertThat(metadata).isEmpty()
    }

    @Test
    fun convertToMetadataWorksCorrectlyForNonEmptyJson() {
        val metadataJson = """
            {
             "foo": "bar",
             "hello": 123
            }
        """.trimIndent()

        val metadata = ObjectMapper().convertToMetadata(metadataJson)
        assertThat(metadata).containsExactly(
            entry("foo", "bar"),
            entry("hello", 123)
        )
    }
}
