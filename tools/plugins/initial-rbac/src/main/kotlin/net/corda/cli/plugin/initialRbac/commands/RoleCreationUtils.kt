package net.corda.cli.plugin.initialRbac.commands

import net.corda.cli.plugins.common.RestClientUtils.createHttpRpcClient
import net.corda.cli.plugins.common.RestClientUtils.executeWithRetry
import net.corda.cli.plugins.common.HttpRpcCommand
import net.corda.libs.permissions.endpoints.v1.permission.PermissionEndpoint
import net.corda.libs.permissions.endpoints.v1.permission.types.BulkCreatePermissionsRequestType
import net.corda.libs.permissions.endpoints.v1.permission.types.CreatePermissionType
import net.corda.libs.permissions.endpoints.v1.permission.types.PermissionType
import net.corda.libs.permissions.endpoints.v1.role.RoleEndpoint
import net.corda.libs.permissions.endpoints.v1.role.types.CreateRoleType
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.time.Duration
import java.time.temporal.ChronoUnit

internal object RoleCreationUtils {

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

        val start = System.currentTimeMillis()

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

            val roleId = executeWithRetry(waitDuration, "Creating role: $roleName") {
                roleEndpoint.createRole(CreateRoleType(roleName, null)).responseBody.id
            }

            createHttpRpcClient(PermissionEndpoint::class).use { permissionEndpointClient ->
                val permissionEndpoint = executeWithRetry(waitDuration, "Start of permissions HTTP endpoint") {
                    permissionEndpointClient.start().proxy
                }

                val bulkRequest = BulkCreatePermissionsRequestType(permissionsToCreate.map { entry ->
                    CreatePermissionType(
                        PermissionType.ALLOW,
                        entry.permissionString,
                        null,
                        entry.vnodeShortHash
                    )
                }.toSet(), setOf(roleId))

                executeWithRetry(waitDuration, "Creating and assigning permissions to the role") {
                    permissionEndpoint.createAndAssignPermissions(bulkRequest)
                }
            }

            val end = System.currentTimeMillis()

            sysOut.info("Successfully created $roleName with id: $roleId and assigned permissions. " +
                    "Elapsed time: ${end - start}ms.")
        }

        return 0
    }
}