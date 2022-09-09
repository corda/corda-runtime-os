package net.corda.simulator.runtime.signing

import net.corda.simulator.crypto.HsmCategory
import java.security.KeyPairGenerator
import java.security.PublicKey
import java.security.SecureRandom
import java.security.spec.ECGenParameterSpec

class BaseSimKeyStore : SimKeyStore {

    private val keys = HashMap<PublicKey, KeyParameters>()
    private val keyGenerator = KeyPairGenerator.getInstance("EC")

    init {
        keyGenerator.initialize(ECGenParameterSpec("secp256k1"), SecureRandom())
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
