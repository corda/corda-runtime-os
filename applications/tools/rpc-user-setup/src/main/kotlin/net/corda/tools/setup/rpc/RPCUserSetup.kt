package net.corda.tools.setup.rpc

import com.typesafe.config.ConfigFactory
import net.corda.configuration.read.ConfigurationReadService
import net.corda.libs.configuration.SmartConfig
import net.corda.libs.configuration.SmartConfigFactory
import net.corda.libs.permissions.manager.common.PermissionTypeDto
import net.corda.libs.permissions.manager.request.AddRoleToUserRequestDto
import net.corda.libs.permissions.manager.request.CreateUserRequestDto
import net.corda.libs.permissions.manager.request.GetPermissionRequestDto
import net.corda.libs.permissions.manager.request.GetUserRequestDto
import net.corda.osgi.api.Application
import net.corda.osgi.api.Shutdown
import net.corda.permissions.service.PermissionServiceComponent
import net.corda.v5.base.util.contextLogger
import net.corda.v5.base.util.debug
import net.corda.v5.base.util.seconds
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
    @Reference(service = ConfigurationReadService::class)
    private val configReadService: ConfigurationReadService,
    @Reference(service = PermissionServiceComponent::class)
    private val permissionServiceComponent: PermissionServiceComponent
) : Application {

    companion object {
        private val logger = contextLogger()
        private const val MSG_CONFIG_PATH = "messaging"
        private const val ALL_ALLOWED_PERMISSION = ".*"

        private fun waitingLoop(
            duration: Duration,
            waitBetween: Duration = Duration.ofMillis(100),
            waitBefore: Duration = waitBetween,
            binaryBlock: () -> Boolean) {
            val end = System.nanoTime() + duration.toNanos()
            var times = 0

            if (!waitBefore.isZero) Thread.sleep(waitBefore.toMillis())

            while (System.nanoTime() < end) {
                if(binaryBlock()) {
                    logger.info("Target state reached after trying $times times.")
                    return
                }
                if (!waitBetween.isZero) Thread.sleep(waitBetween.toMillis())
                times++
            }

            throw IllegalStateException("Failed to reach target state after $duration; attempted $times times.")
        }
    }

    private val actorName = this::class.java.simpleName

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

                permissionServiceComponent.start()
                configReadService.start()
                configReadService.bootstrapConfig(getBootstrapConfig(params))

                // Bootstrapping is done asynchronously in a separate thread, it may take sometime to propagate
                waitingLoop(Duration.parse(params.waitForReadinessDuration)) {
                    permissionServiceComponent.isRunning
                }
                try {
                    createUser(params)
                    val roleId = getOrCreateRole()
                    assignRoleToUser(params.userCreationParams.loginName, roleId)
                    logger.info("Successfully created superuser: ${params.userCreationParams.loginName}")
                } catch (ex: Exception) {
                    logger.error("Unexpected error", ex)
                    throw ex
                }
                finally {
                    permissionServiceComponent.stop()
                    configReadService.stop()
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

    private fun getBootstrapConfig(defaultParams: RPCUserSetupParams): SmartConfig {
        val messagingParamsMap = defaultParams.messagingParams.mapKeys { (key, _) -> "$MSG_CONFIG_PATH.${key.trim()}" }

        val config = ConfigFactory.parseMap(messagingParamsMap)

        val secretsConfig = ConfigFactory.empty()
        val bootConfig = SmartConfigFactory.create(secretsConfig).create(config)
        logger.debug { "Tool boot config\n: ${bootConfig.root().render()}" }

        return bootConfig
    }

    private fun createUser(params: RPCUserSetupParams) {

        // Check if user already exists
        val existingUser = permissionServiceComponent.permissionManager.getUser(
            GetUserRequestDto(
                actorName,
                params.userCreationParams.loginName
            )
        )

        if (existingUser != null) {
            throw IllegalStateException("User: ${params.userCreationParams.loginName} cannot be created " +
                    "as it already exists:\n$existingUser")
        }

        val userDto = with(params.userCreationParams) {
            val passwordExpiryInstant =
                if (password == null) null else Instant.now().plus(Duration.parse(passwordExpiry))
            CreateUserRequestDto(
                loginName, loginName, loginName, true, password,
                passwordExpiryInstant, null
            )
        }
        permissionServiceComponent.permissionManager.createUser(userDto)
    }

    private fun getOrCreateRole(): String {
        // Tries to locate and verify the role, if the role cannot be found or unsuitable then creates one
        return findRole() ?: createRole()
    }

    private fun findRole(): String? {
        val rolesFound = permissionServiceComponent.permissionManager.getRolesMatchingName("$actorName.*")
        val roleFound = rolesFound.find {
            if (it.permissions.size != 1 || it.groupVisibility != null) {
                false
            } else {
                val permissionAssociation = it.permissions.first()
                val permissionRequest = GetPermissionRequestDto(actorName, permissionAssociation.id)
                val permission = permissionServiceComponent.permissionManager.getPermission(permissionRequest)
                if (permission == null) {
                    false
                } else {
                    permission.groupVisibility == null && permission.permissionType == PermissionTypeDto.ALLOW &&
                            permission.permissionString == ALL_ALLOWED_PERMISSION
                }
            }
        }
        logger.debug { "Found role: $roleFound" }
        return roleFound?.id
    }

    private fun createRole(): String {
        TODO("Not yet implemented")
    }

    private fun assignRoleToUser(loginName: String, roleId: String) {
        val request = AddRoleToUserRequestDto(actorName, loginName, roleId)
        permissionServiceComponent.permissionManager.addRoleToUser(request)
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

    @Option(names = ["-w", "--waitForReadinessDuration"], description = ["Password expiry in ISO-8601 format. E.g. 'PT20S'. " +
            "Defaulted to 20 seconds."])
    var waitForReadinessDuration: String = 20.seconds.toString()

    @Mixin
    var userCreationParams = UserCreationParams()

    override fun toString(): String {
        return "messagingParams : $messagingParams, userCreationParams: { $userCreationParams }"
    }
}