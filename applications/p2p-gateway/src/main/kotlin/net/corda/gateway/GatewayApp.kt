package net.corda.gateway

import net.corda.configuration.read.ConfigurationReadService
import net.corda.libs.configuration.write.CordaConfigurationKey
import net.corda.libs.configuration.write.CordaConfigurationVersion
import net.corda.libs.configuration.write.factory.ConfigWriterFactory
import net.corda.osgi.api.Application
import net.corda.osgi.api.Shutdown
import net.corda.p2p.gateway.Gateway
import net.corda.p2p.gateway.GatewayFactory
import org.osgi.framework.FrameworkUtil
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import kotlin.concurrent.thread

@Component
class GatewayApp @Activate constructor(
    @Reference(service = Shutdown::class)
    private val shutDownService: Shutdown,
    @Reference(service = ConfigurationReadService::class)
    private val configurationReadService: ConfigurationReadService,
    @Reference(service = ConfigWriterFactory::class)
    private val configWriterFactory: ConfigWriterFactory,
    @Reference(service = GatewayFactory::class)
    private val gatewayFactory: GatewayFactory,

) : Application {
    companion object {
        private val consoleLogger: Logger = LoggerFactory.getLogger("Console")
    }
    private var gateway: Gateway? = null

    override fun startup(args: Array<String>) {
        val arguments = CliArguments.parse(args)

        if (arguments.helpRequested) {
            shutDownService.shutdown(FrameworkUtil.getBundle(this::class.java))
        } else {

            configurationReadService.start()
            configurationReadService.bootstrapConfig(arguments.kafkaConfigurationConfig)

            val writer = configWriterFactory.createWriter(
                arguments.configTopicName,
                arguments.kafkaConfigurationConfig
            )
            writer.updateConfiguration(
                CordaConfigurationKey(
                    "p2p-gateway",
                    CordaConfigurationVersion("p2p", 1, 0),
                    CordaConfigurationVersion("gateway", 1, 0)
                ),
                arguments.gatewayConfiguration
            )

            consoleLogger.info("Starting gateway")
            gateway = gatewayFactory.createGateway(arguments.kafkaConfigurationConfig).also { gateway ->
                gateway.start()

                thread(isDaemon = true) {
                    while (!gateway.isRunning) {
                        consoleLogger.info("Waiting for gateway to start...")
                        Thread.sleep(1000)
                    }
                    consoleLogger.info("Gateway is running - HTTP server is ${arguments.hostname}:${arguments.port}")
                }
            }
        }
    }

    override fun shutdown() {
        consoleLogger.info("Closing gateway")
        gateway?.close()
        consoleLogger.info("Gateway closed")
    }
}
