package net.corda.libs.permissions.storage.writer.impl.user

import net.corda.data.permissions.management.user.CreateUserRequest
import net.corda.data.permissions.User as AvroUser

interface UserWriter {
    fun createUser(request: CreateUserRequest, requestUserId: String): AvroUser
}