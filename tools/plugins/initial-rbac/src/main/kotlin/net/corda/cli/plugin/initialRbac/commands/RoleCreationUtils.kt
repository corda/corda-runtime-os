package net.corda.cli.plugin.initialRbac.commands

import net.corda.cli.plugins.common.RestCommand
import net.corda.libs.permissions.endpoints.v1.permission.PermissionEndpoint
import net.corda.libs.permissions.endpoints.v1.role.RoleEndpoint
import net.corda.libs.permissions.endpoints.v1.role.types.CreateRoleType
import net.corda.sdk.bootstrap.rbac.PermissionTemplate
import net.corda.sdk.bootstrap.rbac.RoleAndPermissionsCreator
import net.corda.sdk.rest.RestClientUtils.createRestClient
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import kotlin.time.Duration.Companion.seconds

internal object RoleCreationUtils {

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

        val roleId = RoleAndPermissionsCreator().createRoleAndPermissions(
            roleRestClient = roleClient,
            permissionRestClient = permissionClient,
            roleToCreate = CreateRoleType(
                roleName = roleName,
                groupVisibility = null
            ),
            permissionsToCreate = permissionsToCreate,
            wait = waitDurationSeconds.seconds
        ).id

        val end = System.currentTimeMillis()

        sysOut.info(
            "Successfully created $roleName with id: $roleId and assigned permissions. " +
                "Elapsed time: ${end - start}ms."
        )

        return 0
    }
}
