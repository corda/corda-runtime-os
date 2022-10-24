package net.corda.cli.plugin.initialRbac.commands

import net.corda.cli.plugins.common.HttpRpcClientUtils
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
import java.time.Duration
import java.time.temporal.ChronoUnit

internal object RoleCreationUtils {

    private const val UUID_CHARS = "[a-fA-F0-9]"
    const val UUID_REGEX = "$UUID_CHARS{8}-$UUID_CHARS{4}-$UUID_CHARS{4}-$UUID_CHARS{4}-$UUID_CHARS{12}"

    const val VNODE_SHORT_HASH_REGEX = "$UUID_CHARS{12}"

    const val USER_REGEX = "[-._@a-zA-Z0-9]{3,255}"

    /**
     * Checks if role already exists and then does nothing, else:
     * - creates permissions;
     * - creates role;
     * - assigns permissions to the role.
     */
    fun HttpRpcCommand.checkOrCreateRole(roleName: String, permissionsToCreate: Map<String, String>): Int {

        val logger: Logger = LoggerFactory.getLogger(this::class.java)
        val sysOut: Logger = LoggerFactory.getLogger("SystemOut")
        val errOut: Logger = LoggerFactory.getLogger("SystemErr")

        logger.info("Running ${this.javaClass.simpleName}")

        createHttpRpcClient(RoleEndpoint::class).use { roleEndpointClient ->
            val waitDuration = Duration.of(waitDurationSeconds.toLong(), ChronoUnit.SECONDS)
            val roleEndpoint = executeWithRetry(waitDuration, "Start of role HTTP endpoint") {
                roleEndpointClient.start().proxy
            }
            val allRoles = executeWithRetry(waitDuration, "Obtain list of available roles") {
                roleEndpoint.getRoles()
            }
            if (allRoles.any { it.roleName == roleName }) {
                errOut.error("$roleName already exists - nothing to do.")
                return 5
            }

            val permissionIds = createHttpRpcClient(PermissionEndpoint::class).use { permissionEndpointClient ->
                val permissionEndpoint = executeWithRetry(waitDuration, "Start of permissions HTTP endpoint") {
                    permissionEndpointClient.start().proxy
                }
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
                    }.responseBody.id.also {
                        logger.info("Created permission: ${entry.key} with id: $it")
                    }
                }
            }

            val roleId = executeWithRetry(waitDuration, "Creating role: $roleName") {
                roleEndpoint.createRole(CreateRoleType(roleName, null)).responseBody.id
            }
            permissionIds.forEach { permId ->
                executeWithRetry(
                    waitDuration,
                    "Adding permission: $permId",
                    onAlreadyExists = HttpRpcClientUtils::ignore
                ) {
                    roleEndpoint.addPermission(roleId, permId)
                }
            }
            sysOut.info("Successfully created $roleName with id: $roleId and assigned permissions")
        }

        return 0
    }
}