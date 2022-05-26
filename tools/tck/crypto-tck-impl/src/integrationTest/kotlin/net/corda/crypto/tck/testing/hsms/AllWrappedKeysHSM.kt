package net.corda.crypto.tck.testing.hsms

import net.corda.crypto.core.aes.WrappingKey
import net.corda.v5.base.util.contextLogger
import net.corda.v5.cipher.suite.CipherSchemeMetadata
import net.corda.v5.cipher.suite.CryptoService
import net.corda.v5.cipher.suite.GeneratedKey
import net.corda.v5.cipher.suite.KeyGenerationSpec
import net.corda.v5.cipher.suite.SigningSpec
import net.corda.v5.cipher.suite.schemes.KeyScheme
import net.corda.v5.crypto.DigestService
import net.corda.v5.crypto.ECDSA_SECP256K1_CODE_NAME
import net.corda.v5.crypto.ECDSA_SECP256R1_CODE_NAME
import net.corda.v5.crypto.EDDSA_ED25519_CODE_NAME
import net.corda.v5.crypto.GOST3410_GOST3411_CODE_NAME
import net.corda.v5.crypto.RSA_CODE_NAME
import net.corda.v5.crypto.SM2_CODE_NAME
import net.corda.v5.crypto.SPHINCS256_CODE_NAME
import net.corda.v5.crypto.exceptions.CryptoServiceBadRequestException
import net.corda.v5.crypto.exceptions.CryptoServiceException
import java.util.concurrent.ConcurrentHashMap

class AllWrappedKeysHSM(
    private val userName: String,
    private val supportedSchemeCodes: List<String> = listOf(
        RSA_CODE_NAME,
        ECDSA_SECP256K1_CODE_NAME,
        ECDSA_SECP256R1_CODE_NAME,
        EDDSA_ED25519_CODE_NAME,
        SPHINCS256_CODE_NAME,
        SM2_CODE_NAME,
        GOST3410_GOST3411_CODE_NAME
    ),
    private val schemeMetadata: CipherSchemeMetadata,
    private val digestService: DigestService
) : CryptoService {
    companion object {
        private val logger = contextLogger()
    }

    private val masterKeys = ConcurrentHashMap<String, WrappingKey>()

    private val supportedSchemes = produceSupportedSchemes(schemeMetadata, supportedSchemeCodes)

    override fun requiresWrappingKey(): Boolean = true

    override fun supportedSchemes(): List<KeyScheme> = supportedSchemes

    override fun createWrappingKey(masterKeyAlias: String, failIfExists: Boolean, context: Map<String, String>) = try {
        logger.info("createWrappingKey(masterKeyAlias={}, failIfExists={})", masterKeyAlias, failIfExists)
            if (masterKeys[masterKeyAlias] != null) {
                if (failIfExists) {
                    throw CryptoServiceBadRequestException(
                        "There is an existing key with the alias: $masterKeyAlias"
                    )
                } else {
                    logger.info(
                        "Wrapping with alias '$masterKeyAlias' already exists, " +
                                "continue as normal as failIfExists is false"
                    )
                }
            } else {
                val wrappingKey = WrappingKey.generateWrappingKey(schemeMetadata)
                masterKeys[masterKeyAlias] = wrappingKey
            }
    } catch (e: CryptoServiceException) {
        throw e
    } catch (e: Throwable) {
        throw CryptoServiceException("Failed create wrapping key with alias $masterKeyAlias", e)
    }

    override fun generateKeyPair(spec: KeyGenerationSpec, context: Map<String, String>): GeneratedKey {
        TODO("Not yet implemented")
    }

    override fun sign(spec: SigningSpec, data: ByteArray, context: Map<String, String>): ByteArray {
        TODO("Not yet implemented")
    }
}