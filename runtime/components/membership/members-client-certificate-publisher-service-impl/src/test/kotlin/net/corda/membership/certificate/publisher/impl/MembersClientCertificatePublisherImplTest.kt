package net.corda.membership.certificate.publisher.impl

import net.corda.configuration.read.ConfigChangedEvent
import net.corda.configuration.read.ConfigurationReadService
import net.corda.libs.configuration.SmartConfig
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.lifecycle.LifecycleEventHandler
import net.corda.lifecycle.LifecycleStatus
import net.corda.lifecycle.RegistrationStatusChangeEvent
import net.corda.lifecycle.Resource
import net.corda.lifecycle.StartEvent
import net.corda.lifecycle.StopEvent
import net.corda.membership.lib.grouppolicy.GroupPolicyConstants.PolicyKeys.P2PParameters
import net.corda.membership.lib.grouppolicy.GroupPolicyConstants.PolicyValues.P2PParameters.TlsType
import net.corda.messaging.api.processor.CompactedProcessor
import net.corda.messaging.api.processor.DurableProcessor
import net.corda.messaging.api.subscription.Subscription
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.schema.configuration.ConfigKeys
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.argThat
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class MembersClientCertificatePublisherImplTest {
    private val coordinator = mock<LifecycleCoordinator> {
        on { createManagedResource<Resource>(any(), any()) } doAnswer {
            val generator: () -> Resource = it.getArgument(1)
            generator.invoke()
        }
    }
    private val handler = argumentCaptor<LifecycleEventHandler>()
    private val coordinatorFactory = mock<LifecycleCoordinatorFactory> {
        on { createCoordinator(any(), handler.capture()) } doReturn coordinator
    }
    private val subscriber = mock<Subscription<Any, Any>>()
    private val subscriptionFactory = mock<SubscriptionFactory> {
        on {
            createDurableSubscription(
                any(),
                any<DurableProcessor<Any, Any>>(),
                any(),
                anyOrNull(),
            )
        } doReturn subscriber
    }
    private val configurationReadService = mock<ConfigurationReadService>()

    private val impl = MembersClientCertificatePublisherImpl(
        coordinatorFactory,
        subscriptionFactory,
        configurationReadService
    )

    @Test
    fun `start event follow changes`() {
        handler.firstValue.processEvent(StartEvent(), coordinator)

        verify(coordinator)
            .followStatusChangesByName(
                setOf(
                    LifecycleCoordinatorName.forComponent<ConfigurationReadService>(),
                )
            )
    }

    @Test
    fun `stop event close resources`() {
        handler.firstValue.processEvent(StopEvent(), coordinator)

        verify(coordinator)
            .closeManagedResources(
                argThat {
                    this.size == 3
                }
            )
    }

    @Test
    fun `stop event set status to down`() {
        handler.firstValue.processEvent(StopEvent(), coordinator)

        verify(coordinator)
            .updateStatus(LifecycleStatus.DOWN)
    }

    @Test
    fun `RegistrationStatusChangeEvent UP will listen to configuration changes`() {
        handler.firstValue.processEvent(
            RegistrationStatusChangeEvent(
                mock(),
                LifecycleStatus.UP,
            ),
            coordinator
        )

        verify(configurationReadService).registerComponentForUpdates(
            coordinator,
            setOf(
                ConfigKeys.BOOT_CONFIG,
                ConfigKeys.MESSAGING_CONFIG,
                ConfigKeys.P2P_GATEWAY_CONFIG,
            ),
        )
    }

    @Test
    fun `RegistrationStatusChangeEvent DOWN will stop listen to configuration changes`() {
        handler.firstValue.processEvent(
            RegistrationStatusChangeEvent(
                mock(),
                LifecycleStatus.DOWN,
            ),
            coordinator
        )

        verify(coordinator).closeManagedResources(
            argThat {
                size == 1
            }
        )
    }

    @Test
    fun `RegistrationStatusChangeEvent DOWN will update status to down`() {
        handler.firstValue.processEvent(
            RegistrationStatusChangeEvent(
                mock(),
                LifecycleStatus.DOWN,
            ),
            coordinator
        )

        verify(coordinator).updateStatus(LifecycleStatus.DOWN)
    }

    @Test
    fun `ConfigChangedEvent will not start a subscription if MUTUAL TLS is disable`() {
        val gatewayConfiguration = mock<SmartConfig> {
            on { getConfig("sslConfig") } doReturn mock
            on { getString(P2PParameters.TLS_TYPE) } doReturn TlsType.ONE_WAY.toString()
        }
        val configEvent = ConfigChangedEvent(
            keys = emptySet(),
            config = mapOf(ConfigKeys.P2P_GATEWAY_CONFIG to gatewayConfiguration)
        )

        handler.firstValue.processEvent(configEvent, coordinator)

        verify(subscriptionFactory, never()).createCompactedSubscription(
            any(),
            any<CompactedProcessor<Any, Any>>(),
            any()
        )
    }

    @Test
    fun `ConfigChangedEvent will close the subscription if MUTUAL TLS is disable`() {
        val gatewayConfiguration = mock<SmartConfig> {
            on { getConfig("sslConfig") } doReturn mock
            on { getString(P2PParameters.TLS_TYPE) } doReturn TlsType.ONE_WAY.toString()
        }
        val configEvent = ConfigChangedEvent(
            keys = emptySet(),
            config = mapOf(ConfigKeys.P2P_GATEWAY_CONFIG to gatewayConfiguration)
        )

        handler.firstValue.processEvent(configEvent, coordinator)

        verify(coordinator).closeManagedResources(
            argThat {
                size == 1
            }
        )
    }

    @Test
    fun `ConfigChangedEvent will set state to up if MUTUAL TLS is disable`() {
        val gatewayConfiguration = mock<SmartConfig> {
            on { getConfig("sslConfig") } doReturn mock
            on { getString(P2PParameters.TLS_TYPE) } doReturn TlsType.ONE_WAY.toString()
        }
        val configEvent = ConfigChangedEvent(
            keys = emptySet(),
            config = mapOf(ConfigKeys.P2P_GATEWAY_CONFIG to gatewayConfiguration)
        )

        handler.firstValue.processEvent(configEvent, coordinator)

        verify(coordinator).updateStatus(LifecycleStatus.UP)
    }

    @Test
    fun `ConfigChangedEvent will start the subscriber if MUTUAL TLS is enabled`() {
        val gatewayConfiguration = mock<SmartConfig> {
            on { getConfig("sslConfig") } doReturn mock
            on { getString(P2PParameters.TLS_TYPE) } doReturn TlsType.MUTUAL.toString()
        }
        val configEvent = ConfigChangedEvent(
            keys = emptySet(),
            config = mapOf(
                ConfigKeys.P2P_GATEWAY_CONFIG to gatewayConfiguration,
                ConfigKeys.MESSAGING_CONFIG to mock(),
            )
        )

        handler.firstValue.processEvent(configEvent, coordinator)

        verify(subscriber).start()
    }

    @Test
    fun `start will start the coordinator`() {
        impl.start()

        verify(coordinator).start()
    }

    @Test
    fun `stop will stop the coordinator`() {
        impl.stop()

        verify(coordinator).stop()
    }

    @Test
    fun `is running will return the coordinator status`() {
        whenever(coordinator.status).thenReturn(LifecycleStatus.DOWN)

        assertThat(coordinator.isRunning).isFalse
    }
}
