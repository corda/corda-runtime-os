package net.corda.permissions.model

import java.time.Instant

interface RbacDboFactory {

    val allEntityClasses: Set<Class<*>>

    fun createUser(
        id: String, updateTimestamp: Instant, fullName: String, loginName: String, enabled: Boolean,
        saltValue: String?, hashedPassword: String?, passwordExpiry: Instant?, parentGroup: Group?
    ): User
}