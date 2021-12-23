package net.corda.messaging.kafka.subscription.net.corda.messaging.kafka.utils

import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigValueFactory
import net.corda.messaging.kafka.utils.mergeProperties
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class ConfigUtilsTest {

    @Test
    fun testMergeConfig() {
        val config = ConfigFactory.empty()
            .withValue("a.b.key1", ConfigValueFactory.fromAnyRef("value1"))
            .withValue("a.b.key2.key2", ConfigValueFactory.fromAnyRef("value2"))
            .withValue("a.b.key3", ConfigValueFactory.fromAnyRef("value3"))
            .withValue("a.c.key4", ConfigValueFactory.fromAnyRef("value4"))
            .withValue("a.key5", ConfigValueFactory.fromAnyRef("value5"))

        val override = mapOf("key3" to "overrideValue3" )

        val properties = mergeProperties(config, "a.b", override)
        assertThat(properties["key1"]).isEqualTo("value1")
        assertThat(properties["key2.key2"]).isEqualTo("value2")
        assertThat(properties["key3"]).isEqualTo("overrideValue3")
        assertThat(properties["a.c.key4"]).isEqualTo(null)
        assertThat(properties["key4"]).isEqualTo(null)
        assertThat(properties["a.key5"]).isEqualTo(null)
        assertThat(properties["key5"]).isEqualTo(null)
    }
}
