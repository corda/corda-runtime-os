package net.corda.p2p.deployment.commands

import com.typesafe.config.ConfigFactory
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class KafkaSetupTest {
    @Test
    fun `example file is correct`() {
        val exampleData = ClassLoader.getSystemResourceAsStream("p2p-kafka-setup-example.conf")!!
            .reader()
            .use {
                ConfigFactory.parseReader(it)
            }

        val producer = KafkaSetup(
            "",
            3,
            30
        )
        val producedData = producer.createConfiguration().reader().use {
            ConfigFactory.parseReader(it)
        }

        assertThat(exampleData).isEqualTo(producedData)
    }
}
