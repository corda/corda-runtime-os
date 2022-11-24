package net.corda.libs.permissions.endpoints.v1.permission.types

data class BulkCreatePermissionsRequestType(val permissionsToCreate: Set<CreatePermissionType>, val roleIds: Set<String>)
