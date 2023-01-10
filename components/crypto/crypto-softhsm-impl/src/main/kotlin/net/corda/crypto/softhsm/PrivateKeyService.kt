package net.corda.crypto.softhsm

import java.security.PrivateKey

interface PrivateKeyService {
    fun wrap(alias: String, privateKey: PrivateKey): ByteArray
}
