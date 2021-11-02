package net.corda.libs.configuration.read.kafka

import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigRenderOptions
import net.corda.data.config.Configuration
import net.corda.libs.configuration.SmartConfigFactory
import net.corda.libs.configuration.SmartConfigFactoryImpl
import net.corda.libs.configuration.SmartConfigImpl
import net.corda.libs.configuration.read.ConfigListener
import net.corda.libs.configuration.read.kafka.ConfigReaderImpl.Companion.CONFIGURATION_READER
import net.corda.messaging.api.records.Record
import net.corda.messaging.api.subscription.CompactedSubscription
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.messaging.api.subscription.factory.config.SubscriptionConfig
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.mockito.kotlin.mock
import java.io.BufferedReader

class ConfigReaderImplTest {

    private lateinit var configReader: ConfigReaderImpl
    private lateinit var configRepository: ConfigRepository
    private lateinit var configUpdateUtil: ConfigListenerTestUtil
    private val subscriptionFactory: SubscriptionFactory = mock()
    private val subscription: CompactedSubscription<String, Configuration> = mock()
    private val smartConfigFactory: SmartConfigFactory = SmartConfigFactoryImpl()

    @BeforeEach
    fun beforeEach() {
        val config = SmartConfigImpl(BufferedReader(this::class.java.classLoader.getResourceAsStream("kafka.conf").reader()).use {
            ConfigFactory.parseString(it.readText())
        })

        configUpdateUtil = ConfigListenerTestUtil()
        configRepository = ConfigRepository()
        configReader = ConfigReaderImpl(configRepository, subscriptionFactory, config, smartConfigFactory)
        Mockito.`when`(
            subscriptionFactory.createCompactedSubscription(
                SubscriptionConfig(CONFIGURATION_READER, "default-topic"),
                configReader,
                config
            )
        ).thenReturn(subscription)

        Mockito.doNothing().`when`(subscription).start()
        Mockito.doNothing().`when`(subscription).stop()


    }

    @Test
    fun `test register callback before onSnapshot is called`() {
        configReader.registerCallback(configUpdateUtil)

        val configMap = ConfigUtil.testConfigMap()
        val config = configMap["corda.database"]!!
        val avroConfig =
            Configuration(config.root().render(ConfigRenderOptions.concise()), config.getString("componentVersion"))
        configReader.onSnapshot(mapOf("corda.database" to avroConfig))

        Assertions.assertThat(configUpdateUtil.update).isTrue
        Assertions.assertThat(configRepository.getConfigurations()["corda.database"])
            .isEqualTo(configMap["corda.database"])
    }

    @Test
    fun `test register callback after onSnapshot is called`() {
        val configMap = ConfigUtil.testConfigMap()
        val config = configMap["corda.database"]!!
        val avroConfig =
            Configuration(config.root().render(ConfigRenderOptions.concise()), config.getString("componentVersion"))
        configReader.onSnapshot(mapOf("corda.database" to avroConfig))

        configReader.registerCallback(configUpdateUtil)
        Assertions.assertThat(configRepository.getConfigurations()["corda.database"])
            .isEqualTo(configMap["corda.database"])
    }

    @Test
    fun `test callback for onNext`() {
        configReader.registerCallback(configUpdateUtil)

        val configMap = ConfigUtil.testConfigMap()
        val databaseConfig = configMap["corda.database"]!!
        val avroDatabaseConfig =
            Configuration(
                databaseConfig.root().render(ConfigRenderOptions.concise()),
                databaseConfig.getString("componentVersion")
            )

        val topicMap = mutableMapOf("corda.database" to avroDatabaseConfig)

        configReader.onSnapshot(topicMap)

        Assertions.assertThat(configUpdateUtil.update).isTrue
        Assertions.assertThat(configRepository.getConfigurations()["corda.database"])
            .isEqualTo(configMap["corda.database"])

        val securityConfig = configMap["corda.security"]!!
        val avroSecurityConfig =
            Configuration(
                securityConfig.root().render(ConfigRenderOptions.concise()),
                securityConfig.getString("componentVersion")
            )

        topicMap["corda.security"] = avroSecurityConfig
        configReader.onNext(Record("", "corda.security", avroSecurityConfig), null, topicMap)

        Assertions.assertThat(configRepository.getConfigurations()["corda.security"])
            .isEqualTo(configMap["corda.security"])
    }

    @Test
    fun `test unregister callback`() {
        val listenerSubscription = configReader.registerCallback(configUpdateUtil)

        val configMap = ConfigUtil.testConfigMap()
        val databaseConfig = configMap["corda.database"]!!
        val avroDatabaseConfig =
            Configuration(
                databaseConfig.root().render(ConfigRenderOptions.concise()),
                databaseConfig.getString("componentVersion")
            )

        val topicMap = mutableMapOf("corda.database" to avroDatabaseConfig)

        configReader.onSnapshot(topicMap)

        Assertions.assertThat(configUpdateUtil.update).isTrue
        Assertions.assertThat(configUpdateUtil.lastSnapshot["corda.database"])
            .isEqualTo(configMap["corda.database"])

        listenerSubscription.close()

        val securityConfig = configMap["corda.security"]!!
        val avroSecurityConfig =
            Configuration(
                securityConfig.root().render(ConfigRenderOptions.concise()),
                securityConfig.getString("componentVersion")
            )

        topicMap["corda.security"] = avroSecurityConfig
        configReader.onNext(Record("", "corda.security", avroSecurityConfig), null, topicMap)

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

        configReader.registerCallback(listener)

        Assertions.assertThat(lambdaFlag).isFalse
        Assertions.assertThat(changedKeys).isEmpty()
        Assertions.assertThat(configSnapshot).isEmpty()

        val configMap = ConfigUtil.testConfigMap()
        val config = configMap["corda.database"]!!
        val avroConfig =
            Configuration(config.root().render(ConfigRenderOptions.concise()), config.getString("componentVersion"))
        configReader.onSnapshot(mapOf("corda.database" to avroConfig))

        Assertions.assertThat(lambdaFlag).isTrue
        Assertions.assertThat(changedKeys.size)
            .isEqualTo(1)
        Assertions.assertThat(configSnapshot["corda.database"])
            .isEqualTo(configRepository.getConfigurations()["corda.database"])
    }

    @Test
    fun `test that listeners get unregistered correctly when service stops`() {
        configReader.start()
        Assertions.assertThat(configReader.isRunning).isTrue
        configReader.registerCallback(configUpdateUtil)

        val configMap = ConfigUtil.testConfigMap()
        val databaseConfig = configMap["corda.database"]!!
        val avroDatabaseConfig =
            Configuration(
                databaseConfig.root().render(ConfigRenderOptions.concise()),
                databaseConfig.getString("componentVersion")
            )

        val topicMap = mutableMapOf("corda.database" to avroDatabaseConfig)

        configReader.onSnapshot(topicMap)

        Assertions.assertThat(configUpdateUtil.update).isTrue
        Assertions.assertThat(configUpdateUtil.lastSnapshot["corda.database"])
            .isEqualTo(configMap["corda.database"])

        configReader.stop()
        configReader.start()

        val securityConfig = configMap["corda.security"]!!
        val avroSecurityConfig =
            Configuration(
                securityConfig.root().render(ConfigRenderOptions.concise()),
                securityConfig.getString("componentVersion")
            )

        topicMap["corda.security"] = avroSecurityConfig
        configReader.onNext(Record("", "corda.security", avroSecurityConfig), null, topicMap)

        Assertions.assertThat(configUpdateUtil.lastSnapshot["corda.security"]).isNull()

    }
}
