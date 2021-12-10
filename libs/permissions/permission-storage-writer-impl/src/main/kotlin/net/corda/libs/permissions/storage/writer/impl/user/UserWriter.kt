package net.corda.libs.permissions.storage.writer.impl.user

import net.corda.data.permissions.management.user.CreateUserRequest

interface UserWriter {
    fun createUser(request: CreateUserRequest): net.corda.data.permissions.User
}