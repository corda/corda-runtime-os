package net.corda.membership.certificate.publisher.impl

import net.corda.configuration.read.ConfigChangedEvent
import net.corda.configuration.read.ConfigurationReadService
import net.corda.libs.configuration.helper.getConfig
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.lifecycle.LifecycleEvent
import net.corda.lifecycle.LifecycleStatus
import net.corda.lifecycle.RegistrationStatusChangeEvent
import net.corda.lifecycle.StartEvent
import net.corda.lifecycle.StopEvent
import net.corda.lifecycle.createCoordinator
import net.corda.membership.certificate.publisher.MembersClientCertificatePublisher
import net.corda.membership.lib.grouppolicy.GroupPolicyConstants.PolicyValues.P2PParameters.TlsType
import net.corda.messaging.api.subscription.config.SubscriptionConfig
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.schema.Schemas
import net.corda.schema.configuration.ConfigKeys
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference

@Component(service = [MembersClientCertificatePublisher::class])
class MembersClientCertificatePublisherImpl @Activate constructor(
    @Reference(service = LifecycleCoordinatorFactory::class)
    coordinatorFactory: LifecycleCoordinatorFactory,
    @Reference(service = SubscriptionFactory::class)
    private val subscriptionFactory: SubscriptionFactory,
    @Reference(service = ConfigurationReadService::class)
    private val configurationReadService: ConfigurationReadService,
) : MembersClientCertificatePublisher {
    private companion object {
        const val FOLLOW_CHANGES_RESOURCE_NAME = "MembersClientCertificatePublisherImpl.followStatusChangesByName"
        const val WAIT_FOR_CONFIG_RESOURCE_NAME = "MembersClientCertificatePublisherImpl.registerComponentForUpdates"
        const val SUBSCRIPTION_RESOURCE_NAME = "MembersClientCertificatePublisherImpl.subscription"
        const val SUBSCRIPTION_GROUP_NAME = "members-client-certificate-publisher-subscriber"
    }
    private val coordinator = coordinatorFactory.createCoordinator<MembersClientCertificatePublisher> { event, _ ->
        handleEvent(event)
    }
    private fun handleEvent(event: LifecycleEvent) {
        when (event) {
            is StartEvent -> {
                coordinator.createManagedResource(FOLLOW_CHANGES_RESOURCE_NAME) {
                    coordinator.followStatusChangesByName(
                        setOf(
                            LifecycleCoordinatorName.forComponent<ConfigurationReadService>(),
                        )
                    )
                }
            }
            is StopEvent -> {
                coordinator.closeManagedResources(
                    setOf(
                        FOLLOW_CHANGES_RESOURCE_NAME,
                        WAIT_FOR_CONFIG_RESOURCE_NAME,
                        SUBSCRIPTION_RESOURCE_NAME,
                    )
                )
                coordinator.updateStatus(LifecycleStatus.DOWN)
            }
            is RegistrationStatusChangeEvent -> {
                if (event.status == LifecycleStatus.UP) {
                    coordinator.createManagedResource(WAIT_FOR_CONFIG_RESOURCE_NAME) {
                        configurationReadService.registerComponentForUpdates(
                            coordinator,
                            setOf(
                                ConfigKeys.BOOT_CONFIG,
                                ConfigKeys.MESSAGING_CONFIG,
                                ConfigKeys.P2P_GATEWAY_CONFIG,
                            )
                        )
                    }
                } else {
                    coordinator.closeManagedResources(
                        setOf(
                            WAIT_FOR_CONFIG_RESOURCE_NAME
                        )
                    )
                    coordinator.updateStatus(LifecycleStatus.DOWN)
                }
            }
            is ConfigChangedEvent -> {
                val tlsType = TlsType.getClusterType {
                    event.config.getConfig(it)
                }
                if (tlsType == TlsType.MUTUAL) {
                    startSubscription(event)
                }
                coordinator.updateStatus(LifecycleStatus.UP)
            }
        }
    }
    override val isRunning
        get() = coordinator.status == LifecycleStatus.UP

    override fun start() {
        coordinator.start()
    }

    override fun stop() {
        coordinator.stop()
    }

    private fun startSubscription(event: ConfigChangedEvent) {
        coordinator.createManagedResource(SUBSCRIPTION_RESOURCE_NAME) {
            subscriptionFactory.createDurableSubscription(
                subscriptionConfig = SubscriptionConfig(
                    groupName = SUBSCRIPTION_GROUP_NAME,
                    Schemas.Membership.MEMBER_LIST_TOPIC,
                ),
                processor = Processor(),
                messagingConfig = event.config.getConfig(ConfigKeys.MESSAGING_CONFIG),
                partitionAssignmentListener = null,
            ).also {
                it.start()
            }
        }
    }
}
