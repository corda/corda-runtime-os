package net.corda.crypto.impl.persistence

/**
 * Defines a factory which must create a new instance implementing [KeyValuePersistence].
 */
interface KeyValuePersistenceFactoryProvider {
    /**
     * Unique name for factory
     */
    val name: String

    /**
     * Creates a new instance of the key/value persistence for [KeyValuePersistenceFactory].
     */
    fun get(): KeyValuePersistenceFactory
}

