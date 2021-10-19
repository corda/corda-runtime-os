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
                    "gateway-app",
                    CordaConfigurationVersion("p2p", 1, 0),
                    CordaConfigurationVersion("gateway", 1, 0)
                ),
                arguments.gatewayConfiguration
            )

            println("Starting gateway")
            gateway = gatewayFactory.createGateway(arguments.kafkaConfigurationConfig).also { gateway ->
                gateway.start()

                while (!gateway.isRunning) {
                    println("Waiting for gateway to start...")
                    Thread.sleep(1000)
                }
            }

            println("Gateway is running - HTTP server is ${arguments.hostname}:${arguments.port}")
        }
    }

    override fun shutdown() {
        gateway?.close()
    }
}
