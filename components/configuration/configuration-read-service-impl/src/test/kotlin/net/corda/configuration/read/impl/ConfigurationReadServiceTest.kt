package net.corda.configuration.read.impl

import com.typesafe.config.ConfigFactory
import net.corda.configuration.read.ConfigurationReadService
import net.corda.data.config.Configuration
import net.corda.libs.configuration.SmartConfigFactory
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.lifecycle.test.impl.LifecycleTest
import net.corda.messaging.api.subscription.CompactedSubscription
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.schema.configuration.MessagingConfig
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

internal class ConfigurationReadServiceTest {

    companion object {
        private const val BOOT_CONFIG_STRING = """
            ${MessagingConfig.Boot.INSTANCE_ID} = 1
            ${MessagingConfig.Bus.BUS_TYPE} = INMEMORY
        """
    }

    private val bootConfig = SmartConfigFactory.create(ConfigFactory.empty())
        .create(ConfigFactory.parseString(BOOT_CONFIG_STRING))

    @Test
    fun `config read service follows status of subscription`() {
        val subName = LifecycleCoordinatorName("sub")
        val subscription = mock<CompactedSubscription<String, Configuration>>().apply {
            whenever(subscriptionName).thenReturn(subName)
        }
        val subscriptionFactory = mock<SubscriptionFactory>().apply {
            whenever(createCompactedSubscription<String, Configuration>(any(), any(), any())).thenReturn(subscription)
        }

        LifecycleTest<ConfigurationReadService>().run {
            addDependency(subName)

            val configReadService = ConfigurationReadServiceImpl(coordinatorFactory, subscriptionFactory)
            configReadService.start()
            configReadService.bootstrapConfig(bootConfig)

            verifyIsDown(subName)
            verifyIsDown<ConfigurationReadService>()
            bringDependenciesUp()
            verifyIsUp<ConfigurationReadService>()

            repeat(5) {
                toggleDependency(
                    subName,
                    verificationWhenDown = {
                        verifyIsDown<ConfigurationReadService>()
                    },
                    verificationWhenUp = {
                        verifyIsUp<ConfigurationReadService>()
                    },
                )
            }

            bringDependenciesDown()
            verifyIsDown<ConfigurationReadService>()
        }
    }
}
