package net.corda.processors.p2p.linkmanager.internal

import com.typesafe.config.ConfigValueFactory
import net.corda.configuration.read.ConfigurationReadService
import net.corda.cpiinfo.read.CpiInfoReadService
import net.corda.crypto.client.CryptoOpsClient
import net.corda.libs.configuration.SmartConfig
import net.corda.libs.configuration.merger.ConfigMerger
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.lifecycle.LifecycleEvent
import net.corda.lifecycle.RegistrationHandle
import net.corda.lifecycle.RegistrationStatusChangeEvent
import net.corda.lifecycle.StartEvent
import net.corda.lifecycle.StopEvent
import net.corda.lifecycle.createCoordinator
import net.corda.membership.grouppolicy.GroupPolicyProvider
import net.corda.membership.persistence.client.MembershipQueryClient
import net.corda.membership.read.GroupParametersReaderService
import net.corda.membership.read.MembershipGroupReaderProvider
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.p2p.linkmanager.LinkManager
import net.corda.processors.p2p.linkmanager.LinkManagerProcessor
import net.corda.schema.configuration.MessagingConfig.Subscription.POLL_TIMEOUT
import net.corda.v5.base.util.contextLogger
import net.corda.v5.base.util.debug
import net.corda.virtualnode.read.VirtualNodeInfoReadService
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.slf4j.Logger

@Suppress("LongParameterList", "Unused")
@Component(service = [LinkManagerProcessor::class])
class LinkManagerProcessorImpl @Activate constructor(
    @Reference(service = ConfigMerger::class)
    private val configMerger: ConfigMerger,
    @Reference(service = ConfigurationReadService::class)
    private val configurationReadService: ConfigurationReadService,
    @Reference(service = LifecycleCoordinatorFactory::class)
    private val coordinatorFactory: LifecycleCoordinatorFactory,
    @Reference(service = PublisherFactory::class)
    private val publisherFactory: PublisherFactory,
    @Reference(service = SubscriptionFactory::class)
    private val subscriptionFactory: SubscriptionFactory,
    @Reference(service = GroupPolicyProvider::class)
    private val groupPolicyProvider: GroupPolicyProvider,
    @Reference(service = VirtualNodeInfoReadService::class)
    private val virtualNodeInfoReadService: VirtualNodeInfoReadService,
    @Reference(service = CpiInfoReadService::class)
    private val cpiInfoReadService: CpiInfoReadService,
    @Reference(service = CryptoOpsClient::class)
    private val cryptoOpsClient: CryptoOpsClient,
    @Reference(service = MembershipGroupReaderProvider::class)
    private val membershipGroupReaderProvider: MembershipGroupReaderProvider,
    @Reference(service = MembershipQueryClient::class)
    private val membershipQueryClient: MembershipQueryClient,
    @Reference(service = GroupParametersReaderService::class)
    private val groupParametersReaderService: GroupParametersReaderService,
) : LinkManagerProcessor {

    private companion object {
        val log: Logger = contextLogger()
    }

    private var registration: RegistrationHandle? = null
    private var linkManager: LinkManager? = null

    private val lifecycleCoordinator = coordinatorFactory.createCoordinator<LinkManagerProcessorImpl>(::eventHandler)

    override fun start(bootConfig: SmartConfig, useStubComponents: Boolean) {
        log.info("Link manager processor starting.")
        lifecycleCoordinator.start()
        lifecycleCoordinator.postEvent(BootConfigEvent(bootConfig, useStubComponents))
    }

    override fun stop() {
        log.info("Link manager processor stopping.")
        lifecycleCoordinator.stop()
    }

    private fun eventHandler(event: LifecycleEvent, coordinator: LifecycleCoordinator) {
        log.debug { "Link manager received event $event." }

        when (event) {
            is StartEvent -> {
                configurationReadService.start()
            }
            is RegistrationStatusChangeEvent -> {
                log.info("Link manager processor is ${event.status}")
                coordinator.updateStatus(event.status)
            }
            is BootConfigEvent -> {
                configurationReadService.bootstrapConfig(event.config)

                val linkManager = LinkManager(
                    subscriptionFactory,
                    publisherFactory,
                    coordinatorFactory,
                    configurationReadService,
                    configMerger.getMessagingConfig(event.config),
                    groupPolicyProvider,
                    virtualNodeInfoReadService,
                    cpiInfoReadService,
                    cryptoOpsClient,
                    membershipGroupReaderProvider,
                    membershipQueryClient,
                    groupParametersReaderService
                    //This will be removed once integration with MGM/crypto has been completed.
                )

                this.linkManager = linkManager

                registration?.close()
                registration = lifecycleCoordinator.followStatusChangesByName(
                    setOf(
                        LifecycleCoordinatorName.forComponent<ConfigurationReadService>(),
                        linkManager.dominoTile.coordinatorName
                    )
                )

                linkManager.start()
            }
            is StopEvent -> {
                linkManager?.stop()
                linkManager = null
                registration?.close()
                registration = null
            }
        }
    }

    private fun getMessagingConfig(bootConfig: SmartConfig): SmartConfig {
        return configMerger.getMessagingConfig(bootConfig).withValue(
            // The default value of poll timeout is quite high (6 seconds), so setting it to something lower.
            // Specifically, state & event subscriptions have an issue where they are polling with high timeout on events topic,
            // leading to slow syncing upon startup. See: https://r3-cev.atlassian.net/browse/CORE-3163
            POLL_TIMEOUT,
            ConfigValueFactory.fromAnyRef(100)
        )
    }
}

data class BootConfigEvent(val config: SmartConfig, val useStubComponents: Boolean) : LifecycleEvent