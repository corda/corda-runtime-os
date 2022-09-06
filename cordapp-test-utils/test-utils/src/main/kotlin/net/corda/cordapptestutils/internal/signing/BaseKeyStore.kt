package net.corda.cordapptestutils.internal.signing

import net.corda.cordapptestutils.crypto.HsmCategory
import java.security.KeyPairGenerator
import java.security.PublicKey

class BaseKeyStore : KeyStore {

    private val keys = HashMap<PublicKey, KeyParameters>()
    private val keyGenerator = KeyPairGenerator.getInstance("RSA")

    init {
        keyGenerator.initialize(2048)
    }

    override fun generateKey(alias: String, hsmCategory: HsmCategory, scheme: String) : PublicKey {
        val key = keyGenerator.generateKeyPair().public
        keys[key] = KeyParameters(alias, hsmCategory, scheme)
        return key
    }

    override fun getParameters(publicKey: PublicKey): KeyParameters? {
        return keys[publicKey]
    }

}
