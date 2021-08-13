package net.corda.p2p.gateway.keystore

import java.security.KeyStore

class KeystoreHandler {

    private lateinit var tlsKeyStore: KeyStore
    private lateinit var tlsTrustStore: KeyStore

    init {
        val tlsSigningService = DelegatedSigningService()
        tlsKeyStore = DelegatedKeystoreProvider.createKeyStore("DelegatedKeyStore", tlsSigningService, mutableTlsKeystore)
    }
}