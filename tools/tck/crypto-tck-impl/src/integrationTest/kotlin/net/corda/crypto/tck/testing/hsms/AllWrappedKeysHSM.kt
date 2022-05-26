package net.corda.crypto.tck.testing.hsms

import net.corda.v5.cipher.suite.CryptoService
import net.corda.v5.cipher.suite.GeneratedKey
import net.corda.v5.cipher.suite.KeyGenerationSpec
import net.corda.v5.cipher.suite.SigningSpec
import net.corda.v5.cipher.suite.schemes.KeyScheme

class AllWrappedKeysHSM(
    private val userName: String
) : CryptoService {
    override fun createWrappingKey(masterKeyAlias: String, failIfExists: Boolean, context: Map<String, String>) {
        TODO("Not yet implemented")
    }

    override fun generateKeyPair(spec: KeyGenerationSpec, context: Map<String, String>): GeneratedKey {
        TODO("Not yet implemented")
    }

    override fun requiresWrappingKey(): Boolean {
        TODO("Not yet implemented")
    }

    override fun sign(spec: SigningSpec, data: ByteArray, context: Map<String, String>): ByteArray {
        TODO("Not yet implemented")
    }

    override fun supportedSchemes(): List<KeyScheme> {
        TODO("Not yet implemented")
    }
}