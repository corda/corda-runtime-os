package net.corda.cli.plugin.initialconfig

import net.corda.libs.permissions.common.constant.RoleKeys.DEFAULT_SYSTEM_ADMIN_ROLE
import net.corda.libs.permissions.common.constant.UserKeys.DEFAULT_ADMIN_FULL_NAME
import net.corda.permissions.model.ChangeAudit
import net.corda.permissions.model.Permission
import net.corda.permissions.model.PermissionType
import net.corda.permissions.model.RestPermissionOperation
import net.corda.permissions.model.Role
import net.corda.permissions.model.RolePermissionAssociation
import net.corda.permissions.model.RoleUserAssociation
import net.corda.permissions.model.User
import net.corda.permissions.password.PasswordServiceFactory
import java.security.SecureRandom
import java.time.Instant
import java.util.UUID

fun buildRbacConfigSql(
    adminUser: String,
    password: String?,
    requestUser: String
): String {
    val output = StringBuilder()
    val timeStamp = Instant.now()

    val permission = createPermission(timeStamp)
    val permissionAudit = ChangeAudit(
        id = UUID.randomUUID().toString(),
        updateTimestamp = timeStamp,
        actorUser = requestUser,
        changeType = RestPermissionOperation.PERMISSION_INSERT,
        details = "Automated initial set-up"
    )

    val role = createAdminRole(timeStamp)
    val roleAudit = ChangeAudit(
        id = UUID.randomUUID().toString(),
        updateTimestamp = timeStamp,
        actorUser = requestUser,
        changeType = RestPermissionOperation.ROLE_INSERT,
        details = "Automated initial set-up"
    )
    val user = createUser(
        fullName = DEFAULT_ADMIN_FULL_NAME,
        loginName = adminUser,
        password = password,
        timeStamp = timeStamp
    )
    val userAudit = ChangeAudit(
        id = UUID.randomUUID().toString(),
        updateTimestamp = timeStamp,
        actorUser = requestUser,
        changeType = RestPermissionOperation.USER_INSERT,
        details = "Automated initial set-up"
    )

    val rolePermissionAssociation = RolePermissionAssociation(
        id = UUID.randomUUID().toString(),
        role = role,
        permission = permission,
        updateTimestamp = Instant.now()
    )
    val rolePermissionAudit = ChangeAudit(
        id = UUID.randomUUID().toString(),
        updateTimestamp = timeStamp,
        actorUser = requestUser,
        changeType = RestPermissionOperation.ADD_PERMISSION_TO_ROLE,
        details = "Automated initial set-up"
    )

    val roleUserAssociation = RoleUserAssociation(
        id = UUID.randomUUID().toString(),
        role = role,
        user = user,
        updateTimestamp = Instant.now()
    )
    val roleUserAudit = ChangeAudit(
        id = UUID.randomUUID().toString(),
        updateTimestamp = timeStamp,
        actorUser = requestUser,
        changeType = RestPermissionOperation.ADD_ROLE_TO_USER,
        details = "Automated initial set-up"
    )


    output.append(user.toInsertStatement())
    output.append(";\n")
    output.append(userAudit.toInsertStatement())
    output.append(";\n")
    output.append(role.toInsertStatement())
    output.append(";\n")
    output.append(roleAudit.toInsertStatement())
    output.append(";\n")
    output.append(permission.toInsertStatement())
    output.append(";\n")
    output.append(permissionAudit.toInsertStatement())
    output.append(";\n")
    output.append(rolePermissionAssociation.toInsertStatement())
    output.append(";\n")
    output.append(rolePermissionAudit.toInsertStatement())
    output.append(";\n")
    output.append(roleUserAssociation.toInsertStatement())
    output.append(";\n")
    output.append(roleUserAudit.toInsertStatement())
    output.append(";\n")

    return output.toString()
}


@Suppress("LongParameterList")
fun createUser(
    fullName: String,
    loginName: String,
    timeStamp: Instant,
    password: String? = null
): User {

    // Create a hashed password if using
    val hashAndSalt = password?.let {
        val passwordService = PasswordServiceFactory().createPasswordService(SecureRandom())
        passwordService.saltAndHash(password)
    }

    return User(
        fullName = fullName,
        loginName = loginName,
        hashedPassword = hashAndSalt?.value,
        saltValue = hashAndSalt?.salt,
        enabled = true,
        id = UUID.randomUUID().toString(),
        parentGroup = null,
        passwordExpiry = null,
        updateTimestamp = timeStamp
    )
}

private fun createPermission(timeStamp: Instant): Permission {
    return Permission(
        permissionType = PermissionType.ALLOW,
        permissionString = ".*",
        updateTimestamp = timeStamp,
        groupVisibility = null,
        virtualNode = null,
        id = UUID.randomUUID().toString()
    )
}

private fun createAdminRole(timeStamp: Instant): Role {
    return Role(
        name = DEFAULT_SYSTEM_ADMIN_ROLE,
        groupVisibility = null,
        id = UUID.randomUUID().toString(),
        updateTimestamp = timeStamp
    )
}
