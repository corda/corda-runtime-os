package net.corda.tools.setup.rpc

import net.corda.libs.permissions.manager.request.CreateUserRequestDto
import net.corda.osgi.api.Application
import net.corda.osgi.api.Shutdown
import net.corda.permissions.management.PermissionManagementService
import net.corda.v5.base.util.contextLogger
import org.osgi.framework.FrameworkUtil
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import picocli.CommandLine
import picocli.CommandLine.Mixin
import picocli.CommandLine.Option
import java.time.Duration
import java.time.Instant

/** A tool to set up RPC Users */
@Suppress("Unused")
@Component(service = [Application::class])
class RPCUserSetup @Activate constructor(
    @Reference(service = Shutdown::class)
    private val shutDownService: Shutdown,
    @Reference(service = PermissionManagementService::class)
    private val permissionManagementService: PermissionManagementService
) : Application {

    private companion object {
        private val logger = contextLogger()
    }

    /** Parses the arguments, then performs actions necessary. */
    override fun startup(args: Array<String>) {

        logger.info("RPC User setup starting.")

        val params = RPCUserSetupParams()
        val commandLine = CommandLine(params)
        try {
            commandLine.parseArgs(*args)

            if (params.helpRequested) {
                CommandLine.usage(params, System.out)

            } else if (params.versionRequested) {
                val appClass = this::class.java
                val appName = appClass.simpleName
                println("$appName specification version: ${appClass.`package`.specificationVersion ?: "Unknown"}")
                println("$appName implementation version: ${appClass.`package`.implementationVersion ?: "Unknown"}")
            } else {
                logger.info("Params received : $params")

                permissionManagementService.start()
                try {
                    createUser(params)
                }
                finally {
                    permissionManagementService.stop()
                }
            }
        }
        catch (ex: CommandLine.MissingParameterException) {
            logger.error(ex.message)
        }
        finally {
            shutDownService.shutdown(FrameworkUtil.getBundle(this::class.java))
        }
    }

    private fun createUser(params: RPCUserSetupParams) {

        val userDto = with(params.userCreationParams) {
            val passwordExpiryInstant =
                if (password == null) null else Instant.now().plus(Duration.parse(passwordExpiry))
            CreateUserRequestDto(
                loginName, loginName, loginName, true, password,
                passwordExpiryInstant, null
            )
        }
        permissionManagementService.permissionManager.createUser(userDto)
    }

    override fun shutdown() {
        logger.info("RPC User setup stopping.")
    }
}

private class RPCUserSetupParams {

    @Option(names = ["-h", "--help"], usageHelp = true, description = ["Display help and exit."])
    var helpRequested = false

    @Option(names = ["-v", "--version"], versionHelp = true, description = ["Display version and exit."])
    var versionRequested = false

    @Option(names = ["-m", "--messagingParams"], description = ["Messaging parameters for the tool."], required = true)
    var messagingParams = emptyMap<String, String>()

    @Mixin
    var userCreationParams = UserCreationParams()

    override fun toString(): String {
        return "messagingParams : $messagingParams, userCreationParams: { $userCreationParams }"
    }
}