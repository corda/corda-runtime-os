package net.corda.libs.configuration.read.kafka

import com.nhaarman.mockito_kotlin.mock
import com.typesafe.config.ConfigRenderOptions
import net.corda.data.config.Configuration
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class ConfigReadServiceImplTest {

    private lateinit var configReadService: ConfigReadServiceImpl
    private lateinit var configRepository: ConfigRepository
    private val subscriptionFactory: SubscriptionFactory = mock()

    @BeforeEach
    fun beforeEach() {
        configRepository = ConfigRepository()
        configReadService = ConfigReadServiceImpl(configRepository, subscriptionFactory)
    }

    @Test
    fun testRegisterCallback() {
        val configUpdateUtil = ConfigListenerUtil()
        configReadService.registerCallback(configUpdateUtil)
        val configMap = ConfigUtil.testConfigMap()
        val config = configMap["corda.database"]
        val avroConfig =
            Configuration(config?.root()?.render(ConfigRenderOptions.concise()), config?.getString("componentVersion"))
        configReadService.onSnapshot(mapOf("corda.database" to avroConfig))
        Assertions.assertThat(configUpdateUtil.update).isTrue
    }

}
