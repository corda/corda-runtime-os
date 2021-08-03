package net.corda.messaging.emulation.properties

import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigValueFactory
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class InMemPropertiesTest {
    private val config = ConfigFactory.load("tmpInMemDefaults")
        .withValue("a", ConfigValueFactory.fromAnyRef(23))

    @Test
    fun `getIntOrDefault returns value when it exists`() {

        val value = config.getIntOrDefault("a", 100)

        assertThat(value).isEqualTo(23)
    }

    @Test
    fun `getIntOrDefault returns default when value is missing`() {

        val value = config.getIntOrDefault("b", 100)

        assertThat(value).isEqualTo(100)
    }
}
