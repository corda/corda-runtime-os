package net.corda.tools.kafka

import com.typesafe.config.ConfigFactory
import net.corda.osgi.api.Shutdown
import net.corda.schema.Schemas
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.osgi.framework.Bundle

class KafkaConfigUploaderTest {

    companion object {
        const val VALID_CONFIG = """
        corda {    
            foo {
                name="Car"
                age=43
                componentVersion="1.3"
            }
            bar {
                name="Bus"
                componentVersion="11.55"
            }
            packageVersion="5.1"
        }
        """
    }

    private class DummyShutdown : Shutdown {
        override fun shutdown(bundle: Bundle) {}
    }

    @Test
    fun `Can generate records for valid config`() {
        val configUploader = KafkaConfigUploader(mock(), mock(), DummyShutdown())
        val records = configUploader.recordsForConfig(VALID_CONFIG)

        assertThat(records).hasSize(2)
        assertThat(records.map { it.topic }).containsOnly(Schemas.Config.CONFIG_TOPIC)
        assertThat(records.map { it.key }).containsExactlyInAnyOrder("corda.foo", "corda.bar")
        val fooRecord = records.single { it.key == "corda.foo" }
        val fooConfig = ConfigFactory.parseString(fooRecord.value!!.value)
        assertThat(fooConfig.getString("name")).isEqualTo("Car")
        assertThat(fooConfig.getInt("age")).isEqualTo(43)

        val barRecord = records.single { it.key == "corda.bar" }
        val barConfig = ConfigFactory.parseString(barRecord.value!!.value)
        assertThat(barConfig.getString("name")).isEqualTo("Bus")
    }
}