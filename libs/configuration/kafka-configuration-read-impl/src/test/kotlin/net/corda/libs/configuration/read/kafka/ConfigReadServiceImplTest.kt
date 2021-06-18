package net.corda.libs.configuration.read.kafka

import com.nhaarman.mockito_kotlin.mock
import com.typesafe.config.Config
import com.typesafe.config.ConfigRenderOptions
import net.corda.data.config.Configuration
import net.corda.libs.configuration.read.ConfigListener
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
        configReadService.registerCallback(configUpdateUtil)

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

        configReadService.registerCallback(configUpdateUtil)
        Assertions.assertThat(configRepository.getConfigurations()["corda.database"])
            .isEqualTo(configMap["corda.database"])
    }

    @Test
    fun `test callback for onNext`() {
        configReadService.registerCallback(configUpdateUtil)

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

    @Test
    fun `test unregister callback`() {
        val listenerSubscription = configReadService.registerCallback(configUpdateUtil)

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
        Assertions.assertThat(configUpdateUtil.lastSnapshot["corda.database"])
            .isEqualTo(configMap["corda.database"])

        listenerSubscription.close()

        val securityConfig = configMap["corda.security"]!!
        val avroSecurityConfig =
            Configuration(
                securityConfig.root()?.render(ConfigRenderOptions.concise()),
                securityConfig.getString("componentVersion")
            )

        topicMap["corda.security"] = avroSecurityConfig
        configReadService.onNext(Record("", "corda.security", avroSecurityConfig), null, topicMap)

        Assertions.assertThat(configUpdateUtil.lastSnapshot["corda.security"]).isNull()
    }

    @Test
    fun `test registerCallback with lambda`() {
        var lambdaFlag = false
        var changedKeys = setOf<String>()
        var configSnapshot = mapOf<String, Config>()
        val listener = ConfigListener { keys: Set<String>, config: Map<String, Config> ->
            lambdaFlag = true
            changedKeys = keys
            configSnapshot = config
        }

        configReadService.registerCallback(listener)

        Assertions.assertThat(lambdaFlag).isFalse
        Assertions.assertThat(changedKeys).isEmpty()
        Assertions.assertThat(configSnapshot).isEmpty()

        val configMap = ConfigUtil.testConfigMap()
        val config = configMap["corda.database"]!!
        val avroConfig =
            Configuration(config.root()?.render(ConfigRenderOptions.concise()), config.getString("componentVersion"))
        configReadService.onSnapshot(mapOf("corda.database" to avroConfig))

        Assertions.assertThat(lambdaFlag).isTrue
        Assertions.assertThat(changedKeys.size)
            .isEqualTo(1)
        Assertions.assertThat(configSnapshot["corda.database"])
            .isEqualTo(configRepository.getConfigurations()["corda.database"])
    }
}
