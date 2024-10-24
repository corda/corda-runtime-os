package net.corda.libs.permissions.manager.impl.converter

import net.corda.data.permissions.ChangeDetails
import net.corda.data.permissions.Permission
import net.corda.data.permissions.PermissionAssociation
import net.corda.data.permissions.PermissionType
import net.corda.data.permissions.Property
import net.corda.data.permissions.Role
import net.corda.data.permissions.User
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

internal class InternalDtoConverterUtilKtTest {

    @Test
    fun `convert simple User`() {
        val now = Instant.now().truncatedTo(ChronoUnit.MILLIS)
        val avroUser = User(
            UUID.randomUUID().toString(),
            0,
            ChangeDetails(now),
            "user-login1",
            "fullName",
            true,
            "hashed-pass",
            "salt",
            now,
            false,
            "parentGroup",
            emptyList(),
            emptyList()
        )
        val userResponseDto = avroUser.convertToResponseDto()

        assertEquals(avroUser.loginName, userResponseDto.loginName)
        assertEquals(avroUser.version, userResponseDto.version)
        assertEquals(avroUser.fullName, userResponseDto.fullName)
        assertEquals(avroUser.lastChangeDetails.updateTimestamp, userResponseDto.lastUpdatedTimestamp)
        assertEquals(avroUser.enabled, userResponseDto.enabled)
        assertEquals(avroUser.ssoAuth, userResponseDto.ssoAuth)
        assertEquals(avroUser.parentGroupId, userResponseDto.parentGroup)
        assertEquals(0, userResponseDto.properties.size)
    }

    @Test
    fun `convert User with properties`() {
        val property1ChangeTimestamp = Instant.now().truncatedTo(ChronoUnit.MILLIS)
        val property2ChangeTimestamp = Instant.now().truncatedTo(ChronoUnit.MILLIS)
        val userWithoutPassword = User(
            UUID.randomUUID().toString(),
            0,
            ChangeDetails(Instant.now()),
            "loginName",
            "fullName",
            true,
            null,
            null,
            null,
            true,
            "parentGroup",
            listOf(
                Property(UUID.randomUUID().toString(), 0, ChangeDetails(property1ChangeTimestamp), "key1", "a@b"),
                Property(UUID.randomUUID().toString(), 0, ChangeDetails(property2ChangeTimestamp), "key2", "c@d")
            ),
            emptyList()
        )
        val userResponseDto = userWithoutPassword.convertToResponseDto()

        assertEquals(userWithoutPassword.fullName, userResponseDto.fullName)
        assertEquals(userWithoutPassword.lastChangeDetails.updateTimestamp, userResponseDto.lastUpdatedTimestamp)
        assertEquals(userWithoutPassword.enabled, userResponseDto.enabled)
        assertNull(userResponseDto.passwordExpiry)
        assertEquals(true, userResponseDto.ssoAuth)
        assertEquals(userWithoutPassword.parentGroupId, userResponseDto.parentGroup)
        assertEquals(2, userResponseDto.properties.size)

        val propertyResponseDtos = userResponseDto.properties
        assertEquals("key1", propertyResponseDtos.first().key)
        assertEquals("a@b", propertyResponseDtos.first().value)
        assertEquals(property1ChangeTimestamp, propertyResponseDtos.first().lastChangedTimestamp)
        assertEquals("key2", propertyResponseDtos.last().key)
        assertEquals("c@d", propertyResponseDtos.last().value)
        assertEquals(property2ChangeTimestamp, propertyResponseDtos.last().lastChangedTimestamp)
    }

    @Test
    internal fun `convert simple role`() {
        val now = Instant.now().truncatedTo(ChronoUnit.MILLIS)
        val avroRole = Role(
            "id",
            0,
            ChangeDetails(now),
            "name",
            "groupVis",
            emptyList()
        )

        val result = avroRole.convertToResponseDto()

        assertEquals("id", result.id)
        assertEquals(0, result.version)
        assertEquals(now, result.lastUpdatedTimestamp)
        assertEquals("name", result.roleName)
        assertEquals("groupVis", result.groupVisibility)
        assertEquals(0, result.permissions.size)
    }

    @Test
    fun `convert role with permissions`() {
        val now = Instant.now().truncatedTo(ChronoUnit.MILLIS)
        val permission = Permission(
            "permId1",
            21,
            ChangeDetails(now),
            "virtNode1",
            PermissionType.DENY,
            "*",
            "group21"
        )
        val permission2 = Permission(
            "permId2",
            2,
            ChangeDetails(now),
            "virtNode2",
            PermissionType.ALLOW,
            "*",
            "group2"
        )
        val avroRole = Role(
            "id",
            0,
            ChangeDetails(now),
            "name",
            "groupVis",
            listOf(
                PermissionAssociation(
                    ChangeDetails(now),
                    permission.id
                ),
                PermissionAssociation(
                    ChangeDetails(now),
                    permission2.id
                )
            )
        )

        val result = avroRole.convertToResponseDto()

        assertEquals("id", result.id)
        assertEquals(0, result.version)
        assertEquals(now, result.lastUpdatedTimestamp)
        assertEquals("name", result.roleName)
        assertEquals("groupVis", result.groupVisibility)
        assertEquals(listOf(permission.id, permission2.id), result.permissions.map { it.id })
    }
}
