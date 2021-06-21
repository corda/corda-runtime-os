package net.corda.p2p.linkmanager

import java.security.PrivateKey

interface LinkManagerCryptoService {

    /**
     * Sign [data] with the private-key [key]
     */
    fun signData(key: PrivateKey, data: ByteArray): ByteArray
}