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

    // first.last@company.com is a valid username, however when encoded in the URL it will be shown as
    // first.last%40company.com
    private const val ALLOWED_USER_URL_CHARS = "[-._a-zA-Z0-9]"
    const val USER_URL_REGEX = "$ALLOWED_USER_URL_CHARS{3,200}[%40]{0,3}$ALLOWED_USER_URL_CHARS{0,50}"

    const val CLIENT_REQ_REGEX = "[-._A-Za-z0-9]{1,250}"

    const val FLOW_NAME_REGEX = "[._\$a-zA-Z0-9]{1,250}"

    fun wildcardMatch(input: String, regex: String): Boolean {
        return input.matches(regex.toRegex(RegexOption.IGNORE_CASE))
    }

    fun HttpRpcCommand.checkOrCreateRole(roleName: String, permissionsToCreate: Map<String, String>): Int {
        return checkOrCreateRole(
            roleName,
            permissionsToCreate.map { PermissionTemplate(it.key, it.value, null) }.toSet()
        )
    }

    /**
     * Checks if role already exists and then does nothing, else:
     * - creates permissions;
     * - creates role;
     * - assigns permissions to the role.
     */
    fun HttpRpcCommand.checkOrCreateRole(roleName: String, permissionsToCreate: Set<PermissionTemplate>): Int {

        val logger: Logger = LoggerFactory.getLogger(this::class.java)
        val sysOut: Logger = LoggerFactory.getLogger("SystemOut")
        val errOut: Logger = LoggerFactory.getLogger("SystemErr")

        logger.info("Running ${this.javaClass.simpleName}")

        createHttpRpcClient(RoleEndpoint::class).use { roleEndpointClient ->
            val waitDuration = Duration.of(waitDurationSeconds.toLong(), ChronoUnit.SECONDS)
            val roleEndpoint = executeWithRetry(waitDuration, "Connect to role HTTP endpoint") {
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
                permissionsToCreate.sortedBy { it.permissionName }.map { entry ->
                    executeWithRetry(waitDuration, "Creating permission: ${entry.permissionName}") {
                        permissionEndpoint.createPermission(
                            CreatePermissionType(
                                PermissionType.ALLOW,
                                entry.permissionString,
                                null,
                                entry.vnodeShortHash
                            )
                        )
                    }.responseBody.id.also {
                        logger.info("Created permission: ${entry.permissionName} with id: $it")
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