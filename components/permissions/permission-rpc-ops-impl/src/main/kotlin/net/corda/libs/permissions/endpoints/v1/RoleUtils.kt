package net.corda.libs.permissions.endpoints.v1

import net.corda.libs.permissions.common.constant.RoleKeys
import net.corda.libs.permissions.manager.response.RoleResponseDto

internal object RoleUtils {

    val RoleResponseDto.initialAdminRole: Boolean
        get() {
            return roleName == RoleKeys.DEFAULT_SYSTEM_ADMIN_ROLE && groupVisibility == null
        }
}