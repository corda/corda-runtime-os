package net.corda.sdk.bootstrap.rbac

import net.corda.rbac.schema.RbacKeys
import net.corda.rbac.schema.RbacKeys.UUID_REGEX
import net.corda.rbac.schema.RbacKeys.VNODE_SHORT_HASH_REGEX
import net.corda.rbac.schema.RbacKeys.VNODE_STATE_REGEX

object Permissions {

    private const val VERSION_PATH_REGEX = "v[_a-zA-Z0-9]{1,30}"

    // temporary regex for testing
    private const val CPI_FILE_CHECKSUM_REGEX = "[a-fA-F0-9]{12}"
    private const val CERTIFICATE_USAGE_REGEX = "^(p2p-tls|p2p-session|code-signer)$"
    private const val TENANT_ID_REGEX = "[a-fA-F0-9]{12}|p2p"
    private const val KEY_ID_REGEX = "[a-fA-F0-9]{12}"
    private const val HSM_CATEGORY_REGEX = "ACCOUNTS|CI|LEDGER|NOTARY|SESSION_INIT|TLS|JWT_KEY"
    private const val ALIAS_REGEX = "[-._A-Za-z0-9]{1,255}"
    private const val KEY_SCHEME_REGEX =
        """
            CORDA.RSA
            |CORDA.ECDSA.SECP256K1
            |CORDA.ECDSA.SECP256R1
            |CORDA.EDDSA.ED25519
            |CORDA.X25519
            |CORDA.SM2
            |CORDA.GOST3410.GOST3411
            |CORDA.SPHINCS-256
            """

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
        // CPI permissions
        "Get all CPIs" to "GET:/api/$VERSION_PATH_REGEX/cpi",
        "CPI upload" to "POST:/api/$VERSION_PATH_REGEX/cpi",
        "CPI upload status" to "GET:/api/$VERSION_PATH_REGEX/cpi/status/$UUID_REGEX",

        // vNode permissions
        "Create vNode" to "POST:/api/$VERSION_PATH_REGEX/virtualnode",
        "Get all vNodes" to "GET:/api/$VERSION_PATH_REGEX/virtualnode",
        "Get a vNode" to "GET:/api/$VERSION_PATH_REGEX/virtualnode/$VNODE_SHORT_HASH_REGEX",
        "Get operation status of vNode" to "GET:/api/$VERSION_PATH_REGEX/virtualnode/status/[a-fA-F0-9]{64}",
        "Update virtual node state" to "PUT:/api/$VERSION_PATH_REGEX/virtualnode/$VNODE_SHORT_HASH_REGEX/state/$VNODE_STATE_REGEX",
        "Upgrade virtual node's CPI" to "PUT:/api/$VERSION_PATH_REGEX/virtualnode/$VNODE_SHORT_HASH_REGEX/cpi/$CPI_FILE_CHECKSUM_REGEX",
        "Update virtual node database" to "PUT:/api/$VERSION_PATH_REGEX/virtualnode/$VNODE_SHORT_HASH_REGEX/db",
        "Get crypto creation schema SQL" to "GET:/api/$VERSION_PATH_REGEX/virtualnode/create/db/crypto",
        "Get uniqueness creation schema SQL" to "GET:/api/$VERSION_PATH_REGEX/virtualnode/create/db/uniqueness",
        "Get vault creation schema SQL" to "GET:/api/$VERSION_PATH_REGEX/virtualnode/create/db/vault/$CPI_FILE_CHECKSUM_REGEX",
        "Get migration schema SQL" to "GET:/api/$VERSION_PATH_REGEX/virtualnode/$VNODE_SHORT_HASH_REGEX/db/vault/$CPI_FILE_CHECKSUM_REGEX",

        // Certificate permissions
        "Import certificate" to "PUT:/api/$VERSION_PATH_REGEX/certificate/cluster/$CERTIFICATE_USAGE_REGEX",
        "Get all certificate aliases" to "GET:/api/$VERSION_PATH_REGEX/certificate/cluster/$CERTIFICATE_USAGE_REGEX",
        "Get certificate by alias" to "GET:/api/$VERSION_PATH_REGEX/certificate/cluster/$VNODE_SHORT_HASH_REGEX/$CERTIFICATE_USAGE_REGEX",
        "Generate CSR for tenant" to "POST:/api/$VERSION_PATH_REGEX/certificate/$TENANT_ID_REGEX/$KEY_ID_REGEX",

        // HSM permissions
        "Get HSM info" to "GET:/api/$VERSION_PATH_REGEX/hsm/$TENANT_ID_REGEX/$HSM_CATEGORY_REGEX",
        "Assign soft HSM to tenant" to "POST:/api/$VERSION_PATH_REGEX/hsm/soft/$TENANT_ID_REGEX/$HSM_CATEGORY_REGEX",

        // Key permissions
        "Generate key pair" to
            "POST:/api/$VERSION_PATH_REGEX/key/" +
            "$TENANT_ID_REGEX/alias/$ALIAS_REGEX/category/$HSM_CATEGORY_REGEX/scheme/$KEY_SCHEME_REGEX",
        "Get keys" to "GET:/api/$VERSION_PATH_REGEX/key/$TENANT_ID_REGEX",
        "Get key schemes" to "GET:/api/$VERSION_PATH_REGEX/key/$TENANT_ID_REGEX/schemes/$HSM_CATEGORY_REGEX",
        "Get key in PEM format" to "GET:/api/$VERSION_PATH_REGEX/key/$TENANT_ID_REGEX/$KEY_ID_REGEX",

        // Member permissions
        "Start registration" to "POST:/api/$VERSION_PATH_REGEX/membership/$VNODE_SHORT_HASH_REGEX",
        "Get registration status" to "GET:/api/$VERSION_PATH_REGEX/membership/$VNODE_SHORT_HASH_REGEX/$UUID_REGEX",
        "Get all members in membership group" to "GET:/api/$VERSION_PATH_REGEX/members/$VNODE_SHORT_HASH_REGEX",

        // MGM permission
        "Get group policy" to "GET:/api/$VERSION_PATH_REGEX/mgm/$VNODE_SHORT_HASH_REGEX/info",

        // Network permissions
        "Configure a holding identity as network participant" to "PUT:/api/$VERSION_PATH_REGEX/network/setup/$VNODE_SHORT_HASH_REGEX",

        // Flow permissions
        "Get all flows status for holding identity" to "GET:/api/$VERSION_PATH_REGEX/flow/$VNODE_SHORT_HASH_REGEX",
        "Get flow status" to "GET:/api/$VERSION_PATH_REGEX/flow/$VNODE_SHORT_HASH_REGEX/$UUID_REGEX",

    ).toMap()
}

data class PermissionTemplate(val permissionName: String, val permissionString: String, val vnodeShortHash: String?)
