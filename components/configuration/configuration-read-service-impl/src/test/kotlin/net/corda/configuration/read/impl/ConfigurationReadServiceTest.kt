package net.corda.configuration.read.impl

import com.typesafe.config.ConfigFactory
import net.corda.configuration.read.ConfigurationReadService
import net.corda.data.config.Configuration
import net.corda.libs.configuration.SmartConfigFactoryFactory
import net.corda.libs.configuration.merger.ConfigMerger
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.lifecycle.test.impl.LifecycleTest
import net.corda.messaging.api.subscription.CompactedSubscription
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.schema.configuration.BootConfig
import net.corda.schema.configuration.BootConfig.INSTANCE_ID
import net.corda.schema.configuration.MessagingConfig
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

internal class ConfigurationReadServiceTest {

    companion object {
        private const val BOOT_CONFIG_STRING = """
            $INSTANCE_ID = 1
        """

        private const val MESSAGING_CONFIG_STRING = """
            ${MessagingConfig.Bus.BUS_TYPE} = INMEMORY
            ${MessagingConfig.Bus.JDBC_USER} = ""
            ${MessagingConfig.Bus.JDBC_PASS} = ""
            $INSTANCE_ID = 1
            ${BootConfig.TOPIC_PREFIX} = ""
        """
    }



    private val bootConfig = SmartConfigFactoryFactory.createWithoutSecurityServices()
        .create(ConfigFactory.parseString(BOOT_CONFIG_STRING))

    private val messagingConfig = SmartConfigFactoryFactory.createWithoutSecurityServices()
        .create(ConfigFactory.parseString(MESSAGING_CONFIG_STRING))

    @Test
    fun `config read service follows status of subscription`() {
        val subName = LifecycleCoordinatorName("sub")
        val subscription = mock<CompactedSubscription<String, Configuration>>().apply {
            whenever(subscriptionName).thenReturn(subName)
        }
        val subscriptionFactory = mock<SubscriptionFactory>().apply {
            whenever(createCompactedSubscription<String, Configuration>(any(), any(), any())).thenReturn(subscription)
        }

        val configMerger = mock<ConfigMerger>().apply {
            whenever(getMessagingConfig(any(), anyOrNull())).thenReturn(messagingConfig)
        }

        LifecycleTest<ConfigurationReadService>{
            addDependency(subName)

            ConfigurationReadServiceImpl(coordinatorFactory, subscriptionFactory, configMerger)
        }.run {
            testClass.start()
            testClass.bootstrapConfig(bootConfig)

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
