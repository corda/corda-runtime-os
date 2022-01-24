package net.corda.tools.setup.rpc

import net.corda.osgi.api.Application
import net.corda.osgi.api.Shutdown
import net.corda.v5.base.util.contextLogger
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import picocli.CommandLine
import picocli.CommandLine.Option

/** A tool to set up RPC Users */
@Suppress("Unused")
@Component(service = [Application::class])
class RPCUserSetup @Activate constructor(
    @Reference(service = Shutdown::class)
    private val shutDownService: Shutdown
) : Application {

    private companion object {
        private val logger = contextLogger()
        private const val RPC_CONFIG_PATH = "rpc"
    }

    /** Parses the arguments, then initialises and starts the [processor]. */
    override fun startup(args: Array<String>) {
        logger.info("RPC User setup starting.")

        val rpcUserSetupParams = RPCUserSetupParams()
        val commandLine = CommandLine(rpcUserSetupParams)
        commandLine.parseArgs(*args)

        logger.info("Params received : $rpcUserSetupParams")
    }

    override fun shutdown() {
        logger.info("RPC User setup stopping.")
    }
}

/** Additional parameters for the RPC worker are added here. */
private class RPCUserSetupParams {
    @Option(names = ["-m", "--messagingParams"], description = ["Messaging parameters for the tool."])
    var messagingParams = emptyMap<String, String>()

    override fun toString(): String {
        return "messagingParams : $messagingParams"
    }
}