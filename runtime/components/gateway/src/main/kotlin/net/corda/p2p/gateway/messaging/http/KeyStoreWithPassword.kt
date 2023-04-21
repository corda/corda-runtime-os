package net.corda.p2p.gateway.messaging.http

import java.security.KeyStore

data class KeyStoreWithPassword(
    val keyStore: KeyStore,
    val password: String,
)
