package net.corda.securitymanager

import org.osgi.service.condpermadmin.ConditionInfo
import org.osgi.service.permissionadmin.PermissionInfo

/**
 *  List of Permissions guarded by a list of conditions with an access decision.
 */
class ConditionalPermission(
    val name: String?,
    val conditions: Array<ConditionInfo>?,
    val permissions: Array<PermissionInfo>?,
    val access: Access
) {

    enum class Access { DENY, ALLOW  }

    constructor(
        condition: ConditionInfo,
        permissions: Array<PermissionInfo>?,
        access: Access
    ) : this(null, arrayOf(condition), permissions, access)
}