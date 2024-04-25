package net.corda.sdk.bootstrap.rbac

import net.corda.rbac.schema.RbacKeys
import net.corda.rbac.schema.RbacKeys.UUID_REGEX
import net.corda.rbac.schema.RbacKeys.VNODE_SHORT_HASH_REGEX
import net.corda.rbac.schema.RbacKeys.VNODE_STATE_REGEX

object Permissions {

    private const val VERSION_PATH_REGEX = "v[_a-zA-Z0-9]{1,30}"

    val cordaDeveloper: Map<String, String> = listOf(
        "Force CPI upload" to "POST:/api/$VERSION_PATH_REGEX/maintenance/virtualnode/forcecpiupload",
        "Resync the virtual node vault" to
            "POST:/api/$VERSION_PATH_REGEX/maintenance/virtualnode/$VNODE_SHORT_HASH_REGEX/vault-schema/force-resync",
    ).toMap()

    fun flowExecutor(vNodeShortHash: String): Set<PermissionTemplate> {
        return setOf(
            // Endpoint level commands
            PermissionTemplate(
                "Start Flow endpoint",
                "POST:/api/$VERSION_PATH_REGEX/flow/$vNodeShortHash",
                null
            ),
            PermissionTemplate(
                "Get status for all flows",
                "GET:/api/$VERSION_PATH_REGEX/flow/$vNodeShortHash",
                null
            ),
            PermissionTemplate(
                "Get status for a specific flow",
                "GET:/api/$VERSION_PATH_REGEX/flow/$vNodeShortHash/${RbacKeys.CLIENT_REQ_REGEX}",
                null
            ),
            PermissionTemplate(
                "Get a list of startable flows",
                "GET:/api/$VERSION_PATH_REGEX/flowclass/$vNodeShortHash",
                null
            ),
            PermissionTemplate(
                "Get status for a specific flow via WebSocket",
                "WS:/api/$VERSION_PATH_REGEX/flow/$vNodeShortHash/${RbacKeys.CLIENT_REQ_REGEX}",
                null
            ),

            // Flow start related
            PermissionTemplate(
                "Start any flow",
                "${RbacKeys.START_FLOW_PREFIX}${RbacKeys.PREFIX_SEPARATOR}${RbacKeys.FLOW_NAME_REGEX}",
                vNodeShortHash
            )
        )
    }

    val userAdmin: Map<String, String> = listOf(
        // User manipulation permissions
        "CreateUsers" to "POST:/api/$VERSION_PATH_REGEX/user",
        "GetUsersV1" to "GET:/api/v1/user\\?loginName=${RbacKeys.USER_URL_REGEX}",
        "GetUsers" to "GET:/api/$VERSION_PATH_REGEX/user/${RbacKeys.USER_URL_REGEX}",
        "ChangeOtherUserPassword" to "POST:/api/$VERSION_PATH_REGEX/user/otheruserpassword",
        "AddRoleToUser" to "PUT:/api/$VERSION_PATH_REGEX/user/${RbacKeys.USER_URL_REGEX}/role/$UUID_REGEX",
        "DeleteRoleFromUser" to "DELETE:/api/$VERSION_PATH_REGEX/user/${RbacKeys.USER_URL_REGEX}/role/$UUID_REGEX",
        "GetPermissionsSummary" to "GET:/api/$VERSION_PATH_REGEX/user/${RbacKeys.USER_URL_REGEX}/permissionSummary",

        // Permission manipulation permissions ;-)
        "CreatePermission" to "POST:/api/$VERSION_PATH_REGEX/permission",
        "BulkCreatePermissions" to "POST:/api/$VERSION_PATH_REGEX/permission/bulk",
        "QueryPermissions" to "GET:/api/$VERSION_PATH_REGEX/permission\\?.*",
        "GetPermission" to "GET:/api/$VERSION_PATH_REGEX/permission/$UUID_REGEX",

        // Role manipulation permissions
        "GetRoles" to "GET:/api/$VERSION_PATH_REGEX/role",
        "CreateRole" to "POST:/api/$VERSION_PATH_REGEX/role",
        "GetRole" to "GET:/api/$VERSION_PATH_REGEX/role/$UUID_REGEX",
        "AddPermissionToRole" to "PUT:/api/$VERSION_PATH_REGEX/role/$UUID_REGEX/permission/$UUID_REGEX",
        "DeletePermissionFromRole" to "DELETE:/api/$VERSION_PATH_REGEX/role/$UUID_REGEX/permission/$UUID_REGEX"
    ).toMap()

    val vNodeCreator: Map<String, String> = listOf(
        // CPI related
        "Get all CPIs" to "GET:/api/$VERSION_PATH_REGEX/cpi",
        "CPI upload" to "POST:/api/$VERSION_PATH_REGEX/cpi",
        "CPI upload status" to "GET:/api/$VERSION_PATH_REGEX/cpi/status/$UUID_REGEX",

        // vNode related
        "Create vNode" to "POST:/api/$VERSION_PATH_REGEX/virtualnode",
        "Get all vNodes" to "GET:/api/$VERSION_PATH_REGEX/virtualnode",
        "Get a vNode" to "GET:/api/$VERSION_PATH_REGEX/virtualnode/$VNODE_SHORT_HASH_REGEX",
        "Update vNode" to "PUT:/api/$VERSION_PATH_REGEX/virtualnode/$VNODE_SHORT_HASH_REGEX", // TBC
        "Update virtual node state" to "PUT:/api/$VERSION_PATH_REGEX/virtualnode/$VNODE_SHORT_HASH_REGEX/state/${VNODE_STATE_REGEX}"
    ).toMap()
}

data class PermissionTemplate(val permissionName: String, val permissionString: String, val vnodeShortHash: String?)
