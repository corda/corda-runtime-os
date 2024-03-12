package net.corda.cli.plugin.initialRbac.commands

import net.corda.cli.plugins.common.RestCommand
import net.corda.libs.permissions.endpoints.v1.permission.PermissionEndpoint
import net.corda.libs.permissions.endpoints.v1.permission.types.BulkCreatePermissionsRequestType
import net.corda.libs.permissions.endpoints.v1.permission.types.CreatePermissionType
import net.corda.libs.permissions.endpoints.v1.permission.types.PermissionType
import net.corda.libs.permissions.endpoints.v1.role.RoleEndpoint
import net.corda.libs.permissions.endpoints.v1.role.types.CreateRoleType
import net.corda.sdk.rest.RestClientUtils.createRestClient
import net.corda.sdk.rest.RestClientUtils.executeWithRetry
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import kotlin.time.Duration.Companion.seconds

internal object RoleCreationUtils {

    fun wildcardMatch(input: String, regex: String): Boolean {
        return input.matches(regex.toRegex(RegexOption.IGNORE_CASE))
    }

    fun RestCommand.checkOrCreateRole(roleName: String, permissionsToCreate: Map<String, String>): Int {
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
    fun RestCommand.checkOrCreateRole(roleName: String, permissionsToCreate: Set<PermissionTemplate>): Int {
        val logger: Logger = LoggerFactory.getLogger(this::class.java)
        val sysOut: Logger = LoggerFactory.getLogger("SystemOut")
        val errOut: Logger = LoggerFactory.getLogger("SystemErr")

        logger.info("Running ${this.javaClass.simpleName}")

        val start = System.currentTimeMillis()

        val roleClient = createRestClient(
            restResource = RoleEndpoint::class,
            insecure = insecure,
            minimumServerProtocolVersion = minimumServerProtocolVersion,
            username = username,
            password = password,
            targetUrl = targetUrl
        )
        val permissionClient = createRestClient(
            restResource = PermissionEndpoint::class,
            insecure = insecure,
            minimumServerProtocolVersion = minimumServerProtocolVersion,
            username = username,
            password = password,
            targetUrl = targetUrl
        )

        roleClient.use { roleEndpointClient ->
            val waitDuration = waitDurationSeconds.seconds
            val roleEndpoint = executeWithRetry(
                waitDuration = waitDuration,
                operationName = "Connect to role HTTP endpoint"
            ) {
                roleEndpointClient.start().proxy
            }
            val allRoles = executeWithRetry(
                waitDuration = waitDuration,
                operationName = "Obtain list of available roles"
            ) {
                roleEndpoint.getRoles()
            }
            if (allRoles.any { it.roleName == roleName }) {
                errOut.error("$roleName already exists - nothing to do.")
                return 5
            }

            val roleId = executeWithRetry(
                waitDuration = waitDuration,
                operationName = "Creating role: $roleName"
            ) {
                roleEndpoint.createRole(CreateRoleType(roleName, null)).responseBody.id
            }

            permissionClient.use { permissionEndpointClient ->
                val permissionEndpoint = executeWithRetry(
                    waitDuration = waitDuration,
                    operationName = "Start of permissions HTTP endpoint"
                ) {
                    permissionEndpointClient.start().proxy
                }

                val bulkRequest = BulkCreatePermissionsRequestType(
                    permissionsToCreate.map { entry ->
                        CreatePermissionType(
                            PermissionType.ALLOW,
                            entry.permissionString,
                            null,
                            entry.vnodeShortHash
                        )
                    }.toSet(),
                    setOf(roleId)
                )

                executeWithRetry(
                    waitDuration = waitDuration,
                    operationName = "Creating and assigning permissions to the role"
                ) {
                    permissionEndpoint.createAndAssignPermissions(bulkRequest)
                }
            }

            val end = System.currentTimeMillis()

            sysOut.info(
                "Successfully created $roleName with id: $roleId and assigned permissions. " +
                    "Elapsed time: ${end - start}ms."
            )
        }

        return 0
    }
}
