package net.corda.libs.permissions.manager.impl.converter

import java.time.Instant
import java.util.UUID
import net.corda.data.permissions.ChangeDetails
import net.corda.data.permissions.Property
import net.corda.data.permissions.User
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test


internal class ConverterUtilsKtTest {

    @Test
    fun `convert simple User`() {
        val avroUser = User(
            UUID.randomUUID().toString(),
            0,
            ChangeDetails(Instant.now(), "me"),
            "fullName",
            true,
            "hashed-pass",
            "salt",
            false,
            "parentGroup",
            emptyList(),
            emptyList()
        )
        val userResponseDto = avroUser.convertAvroUserToUserResponseDto()

        assertEquals(avroUser.fullName, userResponseDto.fullName)
        assertEquals(avroUser.lastChangeDetails.updateTimestamp.toEpochMilli(), userResponseDto.lastUpdatedTimestamp.toEpochMilli())
        assertEquals(avroUser.enabled, userResponseDto.enabled)
        assertEquals(avroUser.ssoAuth, userResponseDto.ssoAuth)
        assertEquals(avroUser.parentGroupId, userResponseDto.parentGroup)
        assertEquals(0, userResponseDto.properties.size)
    }

    @Test
    fun `convert User with properties`() {

        val property1ChangeTimestamp = Instant.now()
        val property2ChangeTimestamp = Instant.now()
        val avroUser = User(
            UUID.randomUUID().toString(),
            0,
            ChangeDetails(Instant.now(), "me"),
            "fullName",
            true,
            "hashed-pass",
            "salt",
            false,
            "parentGroup",
            listOf(
                Property(UUID.randomUUID().toString(), 0, ChangeDetails(property1ChangeTimestamp, "requestUserName"), "key1", "a@b"),
                Property(UUID.randomUUID().toString(), 0, ChangeDetails(property2ChangeTimestamp, "requestUserName"), "key2", "c@d")
            ),
            emptyList()
        )
        val userResponseDto = avroUser.convertAvroUserToUserResponseDto()

        assertEquals(avroUser.fullName, userResponseDto.fullName)
        assertEquals(avroUser.lastChangeDetails.updateTimestamp.toEpochMilli(), userResponseDto.lastUpdatedTimestamp.toEpochMilli())
        assertEquals(avroUser.enabled, userResponseDto.enabled)
        assertEquals(avroUser.ssoAuth, userResponseDto.ssoAuth)
        assertEquals(avroUser.parentGroupId, userResponseDto.parentGroup)
        assertEquals(2, userResponseDto.properties.size)

        val propertyResponseDtos = userResponseDto.properties
        assertEquals("key1", propertyResponseDtos[0].key)
        assertEquals("a@b", propertyResponseDtos[0].value)
        assertEquals(property1ChangeTimestamp.toEpochMilli(), propertyResponseDtos[0].lastChangedTimestamp.toEpochMilli())
        assertEquals("key2", propertyResponseDtos[1].key)
        assertEquals("c@d", propertyResponseDtos[1].value)
        assertEquals(property2ChangeTimestamp.toEpochMilli(), propertyResponseDtos[1].lastChangedTimestamp.toEpochMilli())
    }
}