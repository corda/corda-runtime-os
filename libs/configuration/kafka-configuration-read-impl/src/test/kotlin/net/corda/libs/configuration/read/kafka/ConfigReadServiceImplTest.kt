package net.corda.libs.configuration.read.kafka

import com.nhaarman.mockito_kotlin.mock
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
        configRepository.storeConfiguration(ConfigUtil.testConfigMap())
        configReadService = ConfigReadServiceImpl(configRepository, subscriptionFactory)
    }

    @Test
    fun testGetConfiguration() {
        val config = configReadService.getConfiguration("corda.database")

        Assertions.assertThat(config.getString("transactionIsolationLevel")).isEqualTo("READ_COMMITTED")
        Assertions.assertThat(config.getString("schema")).isEqualTo("corda")
        Assertions.assertThat(config.getBoolean("runMigration")).isEqualTo(true)
        Assertions.assertThat(config.getDouble("componentVersion")).isEqualTo(5.4)
    }

}
