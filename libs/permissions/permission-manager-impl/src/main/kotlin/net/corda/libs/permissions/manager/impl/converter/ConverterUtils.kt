package net.corda.libs.permissions.manager.impl.converter

import net.corda.libs.permissions.manager.response.PropertyResponseDto
import net.corda.libs.permissions.manager.response.UserResponseDto
import net.corda.data.permissions.User as AvroUser

fun AvroUser.convertAvroUserToUserResponseDto(): UserResponseDto {
    return UserResponseDto(
        id,
        version,
        lastChangeDetails.updateTimestamp,
        fullName,
        loginName,
        enabled,
        ssoAuth,
        passwordExpiry,
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
