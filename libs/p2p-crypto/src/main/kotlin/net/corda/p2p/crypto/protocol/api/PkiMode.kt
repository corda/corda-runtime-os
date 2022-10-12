package net.corda.p2p.crypto.protocol.api

import java.security.KeyStore

sealed class PkiMode {
    object NoPki: PkiMode()
    data class Standard(
        val truststore: KeyStore,
        val ourCertificates: List<String>,
        val revocationCheckMode: RevocationCheckMode
    ): PkiMode()
}

enum class RevocationCheckMode {
    OFF, SOFT_FAIL, HARD_FAIL
}