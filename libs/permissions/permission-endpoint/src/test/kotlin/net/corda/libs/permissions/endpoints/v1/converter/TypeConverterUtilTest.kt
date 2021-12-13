package net.corda.libs.permissions.endpoints.v1.converter

import java.time.Instant
import net.corda.libs.permissions.endpoints.v1.role.types.CreateRoleType
import net.corda.libs.permissions.endpoints.v1.user.types.CreateUserType
import net.corda.libs.permissions.manager.response.PermissionResponseDto
import net.corda.libs.permissions.manager.response.PropertyResponseDto
import net.corda.libs.permissions.manager.response.RoleResponseDto
import net.corda.libs.permissions.manager.response.UserResponseDto
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

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
        val type = CreateUserType("name1", "login1", true, "pass", now, "group1")

        val dto = type.convertToDto("me")

        assertEquals("me", dto.requestedBy)
        assertEquals("name1", dto.fullName)
        assertEquals("login1", dto.loginName)
        assertTrue(dto.enabled)
        assertEquals("pass", dto.initialPassword)
        assertEquals(now, dto.passwordExpiry)
        assertEquals("group1", dto.parentGroup)
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
            properties = listOf(PropertyResponseDto(earlier, "key1", "value1"))
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
    }

    @Test
    fun `convert RoleResponseDto to RoleResponseTypes contains PermissionResponseDtos`() {
        val earlier = Instant.now()
        val now = Instant.now()
        val dto = RoleResponseDto(
            id = "id1",
            version = 991,
            lastUpdatedTimestamp = now,
            roleName = "name1",
            groupVisibility = "group1",
            permissions = listOf(
                PermissionResponseDto(
                    "permission1",
                    0,
                    now,
                    "groupVis2",
                    "virtNode3",
                    "ALLOW",
                    "*"
                ),
                PermissionResponseDto(
                    "permission2",
                    1,
                    earlier,
                    "groupVis2",
                    "virtNode3",
                    "DENY",
                    "*"
                ),
            )
        )

        val type = dto.convertToEndpointType()

        assertEquals("id1", type.id)
        assertEquals(991, type.version)
        assertEquals(now, type.updateTimestamp)
        assertEquals("name1", type.roleName)
        assertEquals("group1", type.groupVisibility)
        assertEquals(2, type.permissions.size)

        assertEquals("permission1", type.permissions[0].id)
        assertEquals(0, type.permissions[0].version)
        assertEquals(now, type.permissions[0].updateTimestamp)
        assertEquals("groupVis2", type.permissions[0].groupVisibility)
        assertEquals("virtNode3", type.permissions[0].virtualNode)
        assertEquals("ALLOW", type.permissions[0].permissionType)
        assertEquals("*", type.permissions[0].permissionString)

        assertEquals("permission2", type.permissions[1].id)
        assertEquals(1, type.permissions[1].version)
        assertEquals(earlier, type.permissions[1].updateTimestamp)
        assertEquals("groupVis2", type.permissions[1].groupVisibility)
        assertEquals("virtNode3", type.permissions[1].virtualNode)
        assertEquals("DENY", type.permissions[1].permissionType)
        assertEquals("*", type.permissions[1].permissionString)

    }
}