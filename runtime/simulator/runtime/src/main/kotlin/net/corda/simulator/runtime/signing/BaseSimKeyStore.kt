package net.corda.simulator.runtime.signing

import net.corda.cipher.suite.impl.CipherSchemeMetadataImpl
import net.corda.simulator.crypto.HsmCategory
import net.corda.v5.crypto.KeySchemeCodes.ECDSA_SECP256R1_CODE_NAME
import java.security.KeyPairGenerator
import java.security.PublicKey

/**
 * A store for keys.
 *
 * @see SimKeyStore for details.
 */
class BaseSimKeyStore : SimKeyStore {

    private val keys = HashMap<PublicKey, KeyParameters>()
    private val cipherSchemeMetadata = CipherSchemeMetadataImpl()
    private val scheme = cipherSchemeMetadata.findKeyScheme(ECDSA_SECP256R1_CODE_NAME)
    private val keyPairGenerator = KeyPairGenerator.getInstance(
        scheme.algorithmName,
        cipherSchemeMetadata.providers.getValue(scheme.providerName)
    )

    init {
        keyPairGenerator.initialize(scheme.algSpec, cipherSchemeMetadata.secureRandom)
    }

    override fun generateKey(alias: String, hsmCategory: HsmCategory, scheme: String) : PublicKey {
        val key = keyPairGenerator.generateKeyPair().public
        keys[key] = KeyParameters(alias, hsmCategory, scheme)
        return key
    }

    override fun getParameters(publicKey: PublicKey): KeyParameters? {
        return keys[publicKey]
    }

}
