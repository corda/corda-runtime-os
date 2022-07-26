package net.corda.gateway

import com.typesafe.config.ConfigFactory
import net.corda.libs.configuration.SmartConfigFactory
import net.corda.osgi.api.Application
import net.corda.osgi.api.Shutdown
import net.corda.processors.p2p.gateway.GatewayProcessor
import org.osgi.framework.FrameworkUtil
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference

@Component
@Suppress("LongParameterList")
class GatewayApp @Activate constructor(
    @Reference(service = Shutdown::class)
    private val shutDownService: Shutdown,
    @Reference(service = GatewayProcessor::class)
    private val linkManagerProcessor: GatewayProcessor,
) : Application {

    override fun startup(args: Array<String>) {
        val arguments = CliArguments.parse(args)
        val bootConfig = SmartConfigFactory.create(ConfigFactory.empty()).create(arguments.bootConfiguration)
        if (arguments.helpRequested) {
            shutDownService.shutdown(FrameworkUtil.getBundle(this::class.java))
        } else {
            linkManagerProcessor.start(bootConfig, !arguments.withoutStubs)
        }
    }

    override fun shutdown() {
        linkManagerProcessor.stop()
    }
}
