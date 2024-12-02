package net.corda.libs.permissions.manager.request

class DeleteGroupRequestDto(
    /**
     * ID of the user making the request.
     */
    val requestedBy: String,

    /**
     * ID of the Group to delete.
     */
    val groupId: String
)
