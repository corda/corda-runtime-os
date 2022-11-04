package net.corda.libs.permissions.endpoints.v1.permission.types

data class BulkCreatePermissionsResponseType(val permissionIds: Set<String>, val roleIds: Set<String>)
