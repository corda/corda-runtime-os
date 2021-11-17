package net.corda.libs.permissions.manager.impl.converter

import java.time.Instant
import net.corda.libs.permissions.manager.response.PropertyResponseDto
import net.corda.libs.permissions.manager.response.UserResponseDto
import net.corda.data.permissions.User as AvroUser

fun AvroUser.convertAvroUserToUserResponseDto(): UserResponseDto {
    return UserResponseDto(
        lastChangeDetails.updateTimestamp,
        fullName,
        "todo",// todo add loginName to User object
        enabled,
        ssoAuth,
        Instant.now(),// todo add password expiry to User object
        parentGroupId,
        properties.map {
            PropertyResponseDto(
                it.lastChangeDetails.updateTimestamp,
                it.key,
                it.value
            )
        },
    )
}
