package net.corda.libs.configuration.read.kafka

import com.nhaarman.mockito_kotlin.mock
import com.typesafe.config.ConfigRenderOptions
import net.corda.data.config.Configuration
import net.corda.messaging.api.records.Record
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class ConfigReadServiceImplTest {

    private lateinit var configReadService: ConfigReadServiceImpl
    private lateinit var configRepository: ConfigRepository
    private val subscriptionFactory: SubscriptionFactory = mock()
    private lateinit var configUpdateUtil: ConfigListenerTestUtil

    @BeforeEach
    fun beforeEach() {
        configUpdateUtil = ConfigListenerTestUtil()
        configRepository = ConfigRepository()
        configReadService = ConfigReadServiceImpl(configRepository, subscriptionFactory)
    }

    @Test
    fun `test register callback before onSnapshot is called`() {
        val callbackMap = configReadService.registerCallback(configUpdateUtil)
        Assertions.assertThat(callbackMap).isEmpty()
        Assertions.assertThat(configUpdateUtil.update).isFalse

        val configMap = ConfigUtil.testConfigMap()
        val config = configMap["corda.database"]!!
        val avroConfig =
            Configuration(config.root()?.render(ConfigRenderOptions.concise()), config.getString("componentVersion"))
        configReadService.onSnapshot(mapOf("corda.database" to avroConfig))

        Assertions.assertThat(configUpdateUtil.update).isTrue
        Assertions.assertThat(configRepository.getConfigurations()["corda.database"])
            .isEqualTo(configMap["corda.database"])
    }

    @Test
    fun `test register callback after onSnapshot is called`() {
        val configMap = ConfigUtil.testConfigMap()
        val config = configMap["corda.database"]!!
        val avroConfig =
            Configuration(config.root()?.render(ConfigRenderOptions.concise()), config.getString("componentVersion"))
        configReadService.onSnapshot(mapOf("corda.database" to avroConfig))

        val callbackMap = configReadService.registerCallback(configUpdateUtil)
        Assertions.assertThat(callbackMap["corda.database"]).isEqualTo(configMap["corda.database"])
    }

    @Test
    fun `test callback for onNext`() {
        val callbackMap = configReadService.registerCallback(configUpdateUtil)
        Assertions.assertThat(callbackMap).isEmpty()
        Assertions.assertThat(configUpdateUtil.update).isFalse

        val configMap = ConfigUtil.testConfigMap()
        val databaseConfig = configMap["corda.database"]!!
        val avroDatabaseConfig =
            Configuration(
                databaseConfig.root()?.render(ConfigRenderOptions.concise()),
                databaseConfig.getString("componentVersion")
            )

        val topicMap = mutableMapOf("corda.database" to avroDatabaseConfig)

        configReadService.onSnapshot(topicMap)

        Assertions.assertThat(configUpdateUtil.update).isTrue
        Assertions.assertThat(configRepository.getConfigurations()["corda.database"])
            .isEqualTo(configMap["corda.database"])

        val securityConfig = configMap["corda.security"]!!
        val avroSecurityConfig =
            Configuration(
                securityConfig.root()?.render(ConfigRenderOptions.concise()),
                securityConfig.getString("componentVersion")
            )

        topicMap["corda.security"] = avroSecurityConfig
        configReadService.onNext(Record("", "corda.security", avroSecurityConfig), null, topicMap)

        Assertions.assertThat(configRepository.getConfigurations()["corda.security"])
            .isEqualTo(configMap["corda.security"])
    }
}
