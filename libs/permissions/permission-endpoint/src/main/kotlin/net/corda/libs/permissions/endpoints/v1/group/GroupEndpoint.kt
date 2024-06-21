package net.corda.libs.permissions.endpoints.v1.group

import net.corda.libs.permissions.endpoints.v1.group.types.CreateGroupType
import net.corda.libs.permissions.endpoints.v1.group.types.GroupContentResponseType
import net.corda.libs.permissions.endpoints.v1.group.types.GroupResponseType
import net.corda.rest.RestResource
import net.corda.rest.annotations.ClientRequestBodyParameter
import net.corda.rest.annotations.HttpDELETE
import net.corda.rest.annotations.HttpGET
import net.corda.rest.annotations.HttpPOST
import net.corda.rest.annotations.HttpPUT
import net.corda.rest.annotations.RestPathParameter
import net.corda.rest.response.ResponseEntity

interface GroupEndpoint : RestResource {

    @HttpPOST
    fun createGroup(
        @ClientRequestBodyParameter(description = "Details of the group to be created.")
        createGroupType: CreateGroupType
    ): ResponseEntity<GroupResponseType>

    @HttpPUT
    fun changeParentGroup(
        @RestPathParameter(description = "ID of the group to change parent.")
        groupId: String,
        @ClientRequestBodyParameter(description = "New parent group ID.")
        newParentGroupId: String
    ): ResponseEntity<GroupResponseType>

    @HttpPUT
    fun assignRoleToGroup(
        @RestPathParameter(description = "ID of the group to assign role.")
        groupId: String,
        @ClientRequestBodyParameter(description = "ID of the role to assign.")
        roleId: String
    ): ResponseEntity<GroupResponseType>

    @HttpDELETE
    fun unassignRoleFromGroup(
        @RestPathParameter(description = "ID of the group to unassign role.")
        groupId: String,
        @ClientRequestBodyParameter(description = "ID of the role to unassign.")
        roleId: String
    ): ResponseEntity<GroupResponseType>

    @HttpGET
    fun getGroupContent(
        @RestPathParameter(description = "ID of the group to get content.")
        groupId: String
    ): GroupContentResponseType

    @HttpDELETE
    fun deleteGroup(
        @RestPathParameter(description = "ID of the group to delete.")
        groupId: String
    ): ResponseEntity<GroupResponseType>
}