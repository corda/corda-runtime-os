package net.corda.cli.plugin.initialRbac.commands

import net.corda.cli.plugins.common.HttpRpcClientUtils.createHttpRpcClient
import net.corda.cli.plugins.common.HttpRpcClientUtils.executeWithRetry
import net.corda.cli.plugins.common.HttpRpcCommand
import net.corda.libs.permissions.endpoints.v1.permission.PermissionEndpoint
import net.corda.libs.permissions.endpoints.v1.permission.types.CreatePermissionType
import net.corda.libs.permissions.endpoints.v1.permission.types.PermissionType
import net.corda.libs.permissions.endpoints.v1.role.RoleEndpoint
import net.corda.libs.permissions.endpoints.v1.role.types.CreateRoleType
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import picocli.CommandLine
import java.time.Duration
import java.time.temporal.ChronoUnit.SECONDS
import java.util.concurrent.Callable

private const val CORDA_DEV_ROLE = "CordaDeveloperRole"

@CommandLine.Command(
    name = "corda-developer",
    description = ["Creates a role ('$CORDA_DEV_ROLE') which will permit:",
        "- vNode reset"]
)
class CordaDeveloperSubcommand : HttpRpcCommand(), Callable<Int> {

    private companion object {
        val logger: Logger = LoggerFactory.getLogger(this::class.java)
        val sysOut: Logger = LoggerFactory.getLogger("SystemOut")
        val errOut: Logger = LoggerFactory.getLogger("SystemErr")
    }

    private val permissionsToCreate: Map<String, String> = listOf(
        "Force CPI upload" to "POST:/api/v1/maintenance/virtualnode"
    ).toMap()

    override fun call(): Int {

        logger.info("Running ${this.javaClass.simpleName}")

        createHttpRpcClient(RoleEndpoint::class).use { roleEndpointClient ->
            val waitDuration = Duration.of(waitDurationSeconds.toLong(), SECONDS)
            val roleEndpoint = executeWithRetry(waitDuration, "Start of role HTTP endpoint") {
                roleEndpointClient.start().proxy
            }
            val allRoles = executeWithRetry(waitDuration, "Obtain list of available roles") {
                roleEndpoint.getRoles()
            }
            if (allRoles.any { it.roleName == CORDA_DEV_ROLE }) {
                errOut.error("$CORDA_DEV_ROLE already exists - nothing to do.")
                return 5
            }

            val permissionIds = createHttpRpcClient(PermissionEndpoint::class).use { permissionEndpointClient ->
                val permissionEndpoint = permissionEndpointClient.start().proxy
                permissionsToCreate.toSortedMap().map { entry ->
                    executeWithRetry(waitDuration, "Creating permission: ${entry.key}") {
                        permissionEndpoint.createPermission(
                            CreatePermissionType(
                                PermissionType.ALLOW,
                                entry.value,
                                null,
                                null
                            )
                        )
                    }
                    .responseBody.id.also {
                        logger.info("Created permission: ${entry.key} with id: $it")
                    }
                }
            }

            val roleId = executeWithRetry(waitDuration, "Creating role: $CORDA_DEV_ROLE") {
                roleEndpoint.createRole(CreateRoleType(CORDA_DEV_ROLE, null)).responseBody.id
            }
            permissionIds.forEach { permId ->
                executeWithRetry(waitDuration, "Adding permission: $permId") {
                    roleEndpoint.addPermission(roleId, permId)
                }
            }
            sysOut.info("Successfully created $CORDA_DEV_ROLE with id: $roleId and assigned permissions")
        }

        return 0
    }
}
