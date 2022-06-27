package net.corda.applications.linkmanager

import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigValueFactory
import kotlin.concurrent.thread
import net.corda.configuration.read.ConfigurationReadService
import net.corda.cpiinfo.read.CpiInfoReadService
import net.corda.crypto.client.CryptoOpsClient
import net.corda.libs.configuration.SmartConfig
import net.corda.libs.configuration.SmartConfigFactory
import net.corda.libs.configuration.merger.ConfigMerger
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.membership.grouppolicy.GroupPolicyProvider
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.osgi.api.Application
import net.corda.osgi.api.Shutdown
import net.corda.p2p.linkmanager.LinkManager
import net.corda.p2p.linkmanager.ThirdPartyComponentsMode
import net.corda.schema.configuration.MessagingConfig.Subscription.POLL_TIMEOUT
import net.corda.virtualnode.read.VirtualNodeInfoReadService
import org.osgi.framework.FrameworkUtil
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.slf4j.Logger
import org.slf4j.LoggerFactory

@Component
@Suppress("LongParameterList")
class LinkManagerApp @Activate constructor(
    @Reference(service = Shutdown::class)
    private val shutDownService: Shutdown,
    @Reference(service = ConfigurationReadService::class)
    private val configurationReadService: ConfigurationReadService,
    @Reference(service = SubscriptionFactory::class)
    private val subscriptionFactory: SubscriptionFactory,
    @Reference(service = PublisherFactory::class)
    private val publisherFactory: PublisherFactory,
    @Reference(service = LifecycleCoordinatorFactory::class)
    private val lifecycleCoordinatorFactory: LifecycleCoordinatorFactory,
    @Reference(service = ConfigMerger::class)
    private val configMerger: ConfigMerger,
    @Reference(service = GroupPolicyProvider::class)
    private val groupPolicyProvider: GroupPolicyProvider,
    @Reference(service = VirtualNodeInfoReadService::class)
    private val virtualNodeInfoReadService: VirtualNodeInfoReadService,
    @Reference(service = CpiInfoReadService::class)
    private val cpiInfoReadService: CpiInfoReadService,
    @Reference(service = CryptoOpsClient::class)
    private val cryptoOpsClient: CryptoOpsClient
) : Application {

    companion object {
        private val consoleLogger: Logger = LoggerFactory.getLogger("Console")
    }
    private var linkManager: LinkManager? = null

    override fun startup(args: Array<String>) {
        val arguments = CliArguments.parse(args)

        if (arguments.helpRequested) {
            shutDownService.shutdown(FrameworkUtil.getBundle(this::class.java))
        } else {

            // TODO - move to common worker and pick up secrets params
            consoleLogger.info("Starting the configuration service")
            val secretsConfig = ConfigFactory.empty()
            val bootConfig = SmartConfigFactory.create(secretsConfig).create(arguments.bootConfiguration)
            configurationReadService.start()
            configurationReadService.bootstrapConfig(bootConfig)

            consoleLogger.info("Starting link manager")
            val messagingConfig = getMessagingConfig(bootConfig)
            val thirdPartyComponentsMode = when(arguments.withoutStubs) {
                true -> ThirdPartyComponentsMode.REAL
                false -> ThirdPartyComponentsMode.STUB
            }
            linkManager = LinkManager(
                subscriptionFactory,
                publisherFactory,
                lifecycleCoordinatorFactory,
                configurationReadService,
                messagingConfig,
                groupPolicyProvider,
                virtualNodeInfoReadService,
                cpiInfoReadService,
                cryptoOpsClient,
                thirdPartyComponentsMode,
            ).also { linkmanager ->
                linkmanager.start()

                thread(isDaemon = true) {
                    while (!linkmanager.isRunning) {
                        consoleLogger.info("Waiting for link manager to start...")
                        Thread.sleep(1000)
                    }
                    consoleLogger.info("Link manager is running")
                }
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

    override fun shutdown() {
        if (linkManager != null) {
            consoleLogger.info("Closing link manager")
            linkManager?.stop()
            consoleLogger.info("Link manager closed")
        }
    }

}