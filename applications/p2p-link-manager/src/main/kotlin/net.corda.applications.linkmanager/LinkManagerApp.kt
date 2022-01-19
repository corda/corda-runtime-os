package net.corda.applications.linkmanager

import com.typesafe.config.ConfigFactory
import net.corda.configuration.read.ConfigurationReadService
import net.corda.libs.configuration.SmartConfigFactory
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.osgi.api.Application
import net.corda.osgi.api.Shutdown
import net.corda.p2p.linkmanager.LinkManager
import org.osgi.framework.FrameworkUtil
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import kotlin.concurrent.thread

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

            configurationReadService.start()

            // TODO - move to common worker and pick up secrets params
            val secretsConfig = ConfigFactory.empty()
            val bootConfig = SmartConfigFactory.create(secretsConfig).create(arguments.kafkaNodeConfiguration)
            configurationReadService.bootstrapConfig(bootConfig)

            consoleLogger.info("Starting link manager")
            linkManager = LinkManager(
                subscriptionFactory,
                publisherFactory,
                lifecycleCoordinatorFactory,
                configurationReadService,
                bootConfig,
                arguments.instanceId
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

    override fun shutdown() {
        if (linkManager != null) {
            consoleLogger.info("Closing link manager")
            linkManager?.stop()
            consoleLogger.info("Link manager closed")
        }
    }

}