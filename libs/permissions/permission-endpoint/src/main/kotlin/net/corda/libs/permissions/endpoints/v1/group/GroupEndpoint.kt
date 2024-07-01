package net.corda.libs.permissions.endpoints.v1.group

import net.corda.libs.permissions.endpoints.v1.group.types.CreateGroupType
import net.corda.libs.permissions.endpoints.v1.group.types.GroupContentResponseType
import net.corda.libs.permissions.endpoints.v1.group.types.GroupResponseType
import net.corda.rest.RestResource
import net.corda.rest.SC_CREATED
import net.corda.rest.annotations.ClientRequestBodyParameter
import net.corda.rest.annotations.HttpDELETE
import net.corda.rest.annotations.HttpGET
import net.corda.rest.annotations.HttpPOST
import net.corda.rest.annotations.HttpPUT
import net.corda.rest.annotations.HttpRestResource
import net.corda.rest.annotations.RestApiVersion
import net.corda.rest.annotations.RestPathParameter
import net.corda.rest.response.ResponseEntity

@HttpRestResource(
    name = "RBAC Group",
    description = "The RBAC Group API consists of a number of endpoints enabling group management in the RBAC " +
        "(role-based access control) permission system. You can get details of specified groups, create new groups, " +
        "assign roles to groups and remove roles from groups.",
    path = "group",
    minVersion = RestApiVersion.C5_3
)
interface GroupEndpoint : RestResource {

    @HttpPOST(
        description = "This method creates a new group.",
        responseDescription = """
            A newly created group with the following attributes:
            id: Unique server generated identifier for the group
            version: The version of the group; version 0 is assigned to a newly created group
            updateTimestamp: The date and time when the group was last updated
            name: The name of the group
            parentGroupId: The ID of the parent group
            properties: An optional set of key/value properties associated with a group
            roleAssociations: A set of roles associated with the group""",
        successCode = SC_CREATED
    )
    fun createGroup(
        @ClientRequestBodyParameter(description = "Details of the group to be created.")
        createGroupType: CreateGroupType
    ): ResponseEntity<GroupResponseType>

    @HttpPUT(
        path = "{groupId}/parent/changeParentId/{newParentGroupId}",
        description = "This method changes the parent group of a specified group.",
        responseDescription = """
            The group with the updated parent group with the following attributes:
            id: Unique server generated identifier for the group
            version: The version of the group; version 0 is assigned to a newly created group
            updateTimestamp: The date and time when the group was last updated
            name: The name of the group
            parentGroupId: The ID of the parent group
            properties: An optional set of key/value properties associated with a group
            roleAssociations: A set of roles associated with the group"""
    )
    fun changeParentGroup(
        @RestPathParameter(description = "ID of the group to change parent.")
        groupId: String,
        @RestPathParameter(description = "New parent group ID.")
        newParentGroupId: String
    ): ResponseEntity<GroupResponseType>

    @HttpPUT(
        path = "{groupId}/role/{roleId}",
        description = "This method assigns a role to a specified group.",
        responseDescription = """
            The group with the newly assigned role with the following attributes:
            id: Unique server generated identifier for the group
            version: The version of the group; version 0 is assigned to a newly created group
            updateTimestamp: The date and time when the group was last updated
            name: The name of the group
            parentGroupId: The ID of the parent group
            properties: An optional set of key/value properties associated with a group
            roleAssociations: A set of roles associated with the group"""
    )
    fun addRole(
        @RestPathParameter(description = "ID of the group to assign role.")
        groupId: String,
        @RestPathParameter(description = "ID of the role to assign.")
        roleId: String
    ): ResponseEntity<GroupResponseType>

    @HttpDELETE(
        path = "{groupId}/role/{roleId}",
        description = "This method removes a role from a specified group.",
        responseDescription = """
            The group with the removed role with the following attributes:
            id: Unique server generated identifier for the group
            version: The version of the group; version 0 is assigned to a newly created group
            updateTimestamp: The date and time when the group was last updated
            name: The name of the group
            parentGroupId: The ID of the parent group
            properties: An optional set of key/value properties associated with a group
            roleAssociations: A set of roles associated with the group"""
    )
    fun removeRole(
        @RestPathParameter(description = "ID of the group to unassign role.")
        groupId: String,
        @RestPathParameter(description = "ID of the role to unassign.")
        roleId: String
    ): ResponseEntity<GroupResponseType>

    @HttpGET(
        path = "{groupId}",
        description = "This method retrieves the content of a specified group.",
        responseDescription = """
            The content of the specified group with the following attributes:
            id: Unique server generated identifier for the group
            version: The version of the group; version 0 is assigned to a newly created group
            updateTimestamp: The date and time when the group was last updated
            name: The name of the group
            parentGroupId: The ID of the parent group
            properties: An optional set of key/value properties associated with a group
            roleAssociations: A set of roles associated with the group"""
    )
    fun getGroupContent(
        @RestPathParameter(description = "ID of the group to get content.")
        groupId: String
    ): GroupContentResponseType

    @HttpDELETE(
        path = "{groupId}",
        description = "This method deletes a specified group. The group can only be deleted if it is empty, i.e. having no sub-groups or users inside it.",
        responseDescription = """
            The deleted group with the following attributes:
            id: Unique server generated identifier for the group
            version: The version of the group; version 0 is assigned to a newly created group
            updateTimestamp: The date and time when the group was last updated
            name: The name of the group
            parentGroupId: The ID of the parent group
            properties: An optional set of key/value properties associated with a group
            roleAssociations: A set of roles associated with the group"""
    )
    fun deleteGroup(
        @RestPathParameter(description = "ID of the group to delete.")
        groupId: String
    ): ResponseEntity<GroupResponseType>
}
