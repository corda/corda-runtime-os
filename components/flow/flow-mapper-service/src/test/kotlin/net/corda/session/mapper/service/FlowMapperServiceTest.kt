package net.corda.session.mapper.service

import net.corda.configuration.read.ConfigurationReadService
import net.corda.data.config.Configuration
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.lifecycle.test.impl.LifecycleTest
import net.corda.messaging.api.subscription.CompactedSubscription
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

internal class FlowMapperServiceTest {
    @Test
    fun `flow mapper service correctly responds to dependencies changes`() {
        val subName = LifecycleCoordinatorName("sub")
        val subscription = mock<CompactedSubscription<String, Configuration>>().apply {
            whenever(subscriptionName).thenReturn(subName)
        }
        val subscriptionFactory = mock<SubscriptionFactory>().apply {
            whenever(createCompactedSubscription<String, Configuration>(any(), any(), any())).thenReturn(subscription)
        }

        LifecycleTest<FlowMapperService>().run {
            addDependency<ConfigurationReadService>()
            addDependency(subName)

            val closeable = mock<AutoCloseable>()
            val configReadService = mock<ConfigurationReadService>().apply {
                whenever(registerComponentForUpdates(any(), any())).thenReturn(closeable)
            }

            val test = FlowMapperService(
                coordinatorFactory,
                configReadService,
                subscriptionFactory,
                mock(),
                mock()
            )
            test.start()

            verifyIsDown<FlowMapperService>()
            bringDependenciesUp()
            bringDependencyUp<FlowMapperService>()
            verifyIsUp<FlowMapperService>()

            repeat(5) {
                toggleDependency<ConfigurationReadService>()
            }
            verify(closeable, times(5)).close()
            verify(configReadService, times(6)).registerComponentForUpdates(any(), any())
            bringDependencyDown<ConfigurationReadService>()
        }

    }
}
