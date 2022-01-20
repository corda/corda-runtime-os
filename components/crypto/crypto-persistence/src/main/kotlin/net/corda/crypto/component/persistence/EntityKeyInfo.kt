package net.corda.crypto.component.persistence

/**
 * Holds the key and description what that key means, like 'alias', 'publicKey'
 */
data class EntityKeyInfo(
    val desc: String,
    val key: String
) {
    companion object {
        const val ALIAS = "alias"
        const val PUBLIC_KEY = "publicKey"
    }
}