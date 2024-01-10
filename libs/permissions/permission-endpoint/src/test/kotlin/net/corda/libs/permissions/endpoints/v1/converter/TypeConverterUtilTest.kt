package net.corda.libs.permissions.endpoints.v1.converter

import net.corda.libs.permissions.endpoints.v1.permission.types.CreatePermissionType
import net.corda.libs.permissions.endpoints.v1.permission.types.PermissionType
import java.time.Instant
import net.corda.libs.permissions.endpoints.v1.role.types.CreateRoleType
import net.corda.libs.permissions.endpoints.v1.user.types.CreateUserType
import net.corda.libs.permissions.manager.response.PermissionAssociationResponseDto
import net.corda.libs.permissions.manager.response.PropertyResponseDto
import net.corda.libs.permissions.manager.response.RoleAssociationResponseDto
import net.corda.libs.permissions.manager.response.RoleResponseDto
import net.corda.libs.permissions.manager.response.UserResponseDto
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.*

class TypeConverterUtilTest {
    @Test
    fun `convert CreateRoleType to CreateRoleRequestDto`() {
        val type = CreateRoleType("role1", "group1")

        val dto = type.convertToDto("me")

        assertEquals("me", dto.requestedBy)
        assertEquals("group1", dto.groupVisibility)
        assertEquals("role1", dto.roleName)
    }

    @Test
    fun `convert CreateUserType to CreateUserRequestDto`() {
        val now = Instant.now()
        val parentGroup = UUID.randomUUID().toString()
        val type = CreateUserType("name1", "login1", true, "password", now, parentGroup)

        val dto = type.convertToDto("me")

        assertEquals("me", dto.requestedBy)
        assertEquals("name1", dto.fullName)
        assertEquals("login1", dto.loginName)
        assertTrue(dto.enabled)
        assertEquals("password", dto.initialPassword)
        assertEquals(now, dto.passwordExpiry)
        assertEquals(parentGroup, dto.parentGroup)
    }

    @Test
    fun `convert UserResponseDto to UserResponseType, contains PropertyResponseDtos`() {
        val earlier = Instant.now()
        val now = Instant.now()
        val dto = UserResponseDto(
            id = "id1",
            version = 991,
            lastUpdatedTimestamp = now,
            fullName = "name1",
            loginName = "login1",
            enabled = true,
            ssoAuth = false,
            passwordExpiry = now,
            parentGroup = "group1",
            properties = listOf(PropertyResponseDto(earlier, "key1", "value1")),
            roles = listOf(RoleAssociationResponseDto("roleId1", now))
        )

        val type = dto.convertToEndpointType()

        assertEquals("id1", type.id)
        assertEquals(991, type.version)
        assertEquals(now, type.updateTimestamp)
        assertEquals("name1", type.fullName)
        assertEquals("login1", type.loginName)
        assertTrue(type.enabled)
        assertFalse(type.ssoAuth)
        assertEquals(now, type.passwordExpiry)
        assertEquals("group1", type.parentGroup)
        assertEquals(1, type.properties.size)
        assertEquals(earlier, type.properties[0].lastChangedTimestamp)
        assertEquals("key1", type.properties[0].key)
        assertEquals("value1", type.properties[0].value)
        assertEquals("roleId1", type.roleAssociations[0].roleId)
        assertEquals(now, type.roleAssociations[0].createTimestamp)
    }

    @Test
    fun `convert RoleResponseDto to RoleResponseTypes contains PermissionResponseDtos`() {
        val earlier = Instant.now()
        val now = Instant.now()
        val permissionAssociationResponseDto = PermissionAssociationResponseDto(
            "permission1",
            now
        )
        val permissionAssociationResponseDto2 = PermissionAssociationResponseDto(
            "permission2",
            earlier
        )
        val dto = RoleResponseDto(
            id = "id1",
            version = 991,
            lastUpdatedTimestamp = now,
            roleName = "name1",
            groupVisibility = "group1",
            permissions = listOf(permissionAssociationResponseDto, permissionAssociationResponseDto2)
        )

        val type = dto.convertToEndpointType()

        assertEquals("id1", type.id)
        assertEquals(991, type.version)
        assertEquals(now, type.updateTimestamp)
        assertEquals("name1", type.roleName)
        assertEquals("group1", type.groupVisibility)
        assertEquals(2, type.permissions.size)

        assertEquals(permissionAssociationResponseDto.id, type.permissions[0].id)
        assertEquals(now, type.permissions[0].createdTimestamp)

        assertEquals(permissionAssociationResponseDto2.id, type.permissions[1].id)
        assertEquals(earlier, type.permissions[1].createdTimestamp)
    }

    @Test
    fun `convert CreatePermissionType to CreatePermissionRequestDto`() {
        val createPermissionType = CreatePermissionType(
            PermissionType.ALLOW, "permissionString","group1", "virtualNode")

        val requestedBy = "me"
        val dto = createPermissionType.convertToDto(requestedBy)

        assertEquals(requestedBy, dto.requestedBy)
        assertEquals(createPermissionType.groupVisibility, dto.groupVisibility)
        assertEquals(createPermissionType.permissionString, dto.permissionString)
    }
}