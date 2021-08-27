package net.corda.crypto.impl

/**
 * Defines a factory which must create a new instance implementing [SigningServicePersistentCache]
 */
@FunctionalInterface
interface SigningServicePersistentCacheFactory {
    fun create(): SigningServicePersistentCache
}