package net.corda.simulator.runtime.signing

fun interface KeyStoreFactory {
    fun createKeyStore(): SimKeyStore
}

fun keystoreFactoryBase() = KeyStoreFactory { BaseSimKeyStore() }