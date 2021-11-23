package net.corda.permissions.password

/**
 * Using [String] for storing content as it will need to be stored in the database inside `VARCHAR` column type.
 */
data class PasswordHash(val salt: String, val value: String)