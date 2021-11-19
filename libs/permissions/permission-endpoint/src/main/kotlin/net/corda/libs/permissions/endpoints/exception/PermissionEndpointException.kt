package net.corda.libs.permissions.endpoints.exception

class PermissionEndpointException(
    override val message: String,
    val status: Int
) : Exception(message)