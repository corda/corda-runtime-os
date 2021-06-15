package net.corda.libs.configuration.read.kafka.processor

import com.typesafe.config.ConfigRenderOptions
import net.corda.data.config.Configuration
import net.corda.libs.configuration.read.kafka.ConfigRepository
import net.corda.libs.configuration.read.kafka.ConfigUpdateUtil
import net.corda.libs.configuration.read.kafka.ConfigUtil
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class ConfigCompactedProcessorTest {

    private lateinit var processor: ConfigCompactedProcessor
    private lateinit var repository: ConfigRepository

    @BeforeEach
    fun beforeEach() {
        repository = ConfigRepository()
        processor = ConfigCompactedProcessor(repository)
    }

    @Test
    fun testRegisterCallback() {
        val configUpdateUtil = ConfigUpdateUtil()
        processor.registerCallback(configUpdateUtil)
        val configMap = ConfigUtil.testConfigMap()
        val config = configMap["corda.database"]
        val avroConfig =
            Configuration(config?.root()?.render(ConfigRenderOptions.concise()), config?.getString("componentVersion"))
        processor.onSnapshot(mapOf("corda.database" to avroConfig))
        Assertions.assertThat(configUpdateUtil.update).isTrue
    }
}
