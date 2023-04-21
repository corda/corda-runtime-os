package net.corda.permissions.password

interface PasswordService {

    /**
     * Produces [PasswordHash] from [clearTextPassword] generating random salt value and applying an
     * approved hashing algorithm.
     */
    fun saltAndHash(clearTextPassword: String): PasswordHash

    /**
     * Checks whether supplied [clearTextPassword] matches to a previously hashed value
     */
    fun verifies(clearTextPassword: String, expected: PasswordHash): Boolean
}