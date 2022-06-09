package net.corda.crypto.tck.impl.compliance

import net.corda.crypto.core.CryptoConsts
import net.corda.crypto.core.CryptoTenants
import net.corda.crypto.impl.decorators.requiresWrappingKey
import net.corda.crypto.impl.decorators.supportsKeyDelete
import net.corda.crypto.tck.impl.ComplianceSpec
import net.corda.crypto.tck.impl.CryptoServiceProviderMap
import net.corda.v5.cipher.suite.CRYPTO_CATEGORY
import net.corda.v5.cipher.suite.CRYPTO_KEY_TYPE
import net.corda.v5.cipher.suite.CRYPTO_KEY_TYPE_KEYPAIR
import net.corda.v5.cipher.suite.CRYPTO_KEY_TYPE_WRAPPING
import net.corda.v5.cipher.suite.CRYPTO_TENANT_ID
import net.corda.v5.cipher.suite.CryptoService
import net.corda.v5.cipher.suite.GeneratedKey
import net.corda.v5.cipher.suite.GeneratedPublicKey
import net.corda.v5.cipher.suite.GeneratedWrappedKey
import net.corda.v5.cipher.suite.KeyGenerationSpec
import net.corda.v5.cipher.suite.SigningAliasSpec
import net.corda.v5.cipher.suite.SigningWrappedSpec
import net.corda.v5.cipher.suite.schemes.KeyScheme
import net.corda.v5.crypto.SignatureSpec
import net.corda.v5.crypto.exceptions.CryptoServiceException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.fail
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentLinkedQueue

abstract class AbstractCompliance {
    protected val logger: Logger = LoggerFactory.getLogger(this::class.java)

    protected lateinit var compliance: ComplianceSpec
    protected lateinit var service: CryptoService
    protected lateinit var tenantId: String
    protected var masterKeyAlias: String? = null

    private val generatedAliases = ConcurrentLinkedQueue<String>()

    protected fun setup(spec: ComplianceSpec, providers: CryptoServiceProviderMap) {
        compliance = spec
        service = compliance.createService(providers)
        spec.options.usedSignatureSpecs = service.supportedSchemes.toMap()
            logger.info("serviceName=${compliance.options.serviceName}")
        tenantId = compliance.generateRandomIdentifier()
        if (service.requiresWrappingKey) {
            masterKeyAlias = compliance.generateRandomIdentifier()
            service.createWrappingKey(
                masterKeyAlias!!, true, mapOf(
                    CRYPTO_TENANT_ID to CryptoTenants.CRYPTO
                )
            )
        }
    }

    protected fun deleteKeyPair(hsmAlias: String) {
        if (!service.supportsKeyDelete) {
            logger.info("Service doesn't support key deletion.")
            return
        }
        try {
            logger.info("About to delete {} key.", hsmAlias)
            service.delete(
                hsmAlias, mapOf(
                    CRYPTO_TENANT_ID to tenantId,
                    CRYPTO_KEY_TYPE to CRYPTO_KEY_TYPE_KEYPAIR
                )
            )
        } catch (e: Throwable) {
            logger.warn("Failed to delete a ky with alias=$hsmAlias", e)
        }
    }

    protected fun deleteWrappingKey(alias: String) {
        if (!service.supportsKeyDelete) {
            logger.info("Service doesn't support key deletion.")
            return
        }
        try {
            logger.info("About to delete {} key.", alias)
            service.delete(
                alias, mapOf(
                    CRYPTO_TENANT_ID to tenantId,
                    CRYPTO_KEY_TYPE to CRYPTO_KEY_TYPE_WRAPPING
                )
            )
        } catch (e: Throwable) {
            logger.warn("Failed to delete a ky with alias=$alias", e)
        }
    }

    protected fun `Should generate key with expected key scheme`(
        alias: String?,
        masterKeyAlias: String?,
        keyScheme: KeyScheme
    ): GeneratedKey {
        val key = service.generateKeyPair(
            spec = KeyGenerationSpec(
                keyScheme = keyScheme,
                alias = alias,
                masterKeyAlias = masterKeyAlias,
                secret = compliance.generateRandomIdentifier().toByteArray()
            ),
            context = mapOf(
                CRYPTO_TENANT_ID to tenantId,
                CRYPTO_CATEGORY to CryptoConsts.Categories.LEDGER
            )
        )
        if(key is GeneratedPublicKey && key.hsmAlias.isNotBlank()) {
            generatedAliases.add(key.hsmAlias)
        }
        validateGeneratedKey(key, alias, keyScheme)
        return key
    }

    protected fun `Should be able to sign`(
        key: GeneratedKey,
        keyScheme: KeyScheme,
        signatureSpec: SignatureSpec
    ): List<Experiment> {
        val spec = if (key is GeneratedWrappedKey) {
            SigningWrappedSpec(
                keyMaterial = key.keyMaterial,
                masterKeyAlias = masterKeyAlias,
                encodingVersion = key.encodingVersion,
                keyScheme = keyScheme,
                signatureSpec = signatureSpec
            )
        } else {
            key as GeneratedPublicKey
            val spec = SigningAliasSpec(
                hsmAlias = key.hsmAlias,
                keyScheme = keyScheme,
                signatureSpec = signatureSpec
            )
            assertThrows<CryptoServiceException>(
                "Should throw CryptoServiceException when HSM alias is not known."
            ) {
                service.sign(
                    SigningAliasSpec(
                        hsmAlias = compliance.generateRandomIdentifier(),
                        keyScheme = keyScheme,
                        signatureSpec = signatureSpec
                    ),
                    compliance.generateRandomIdentifier().toByteArray(),
                    mapOf(CRYPTO_TENANT_ID to tenantId)
                )
            }
            spec
        }
        return listOf(
            compliance.generateRandomIdentifier(1).toByteArray(),
            compliance.generateRandomIdentifier(5).toByteArray(),
            ByteArray(97).also { CryptoServiceCompliance.schemeMetadata.secureRandom.nextBytes(it) },
            ByteArray(1673).also { CryptoServiceCompliance.schemeMetadata.secureRandom.nextBytes(it) }
        ).map { clearData ->
            Experiment(
                key = key,
                keyScheme = keyScheme,
                signatureSpec = signatureSpec,
                clearData = clearData,
                signature = service.sign(spec, clearData, mapOf(CRYPTO_TENANT_ID to tenantId))
            )
        }
    }

    private fun validateGeneratedKey(key: GeneratedKey, alias: String?, expected: KeyScheme) {
        val keyScheme = try {
            CryptoServiceCompliance.schemeMetadata.findKeyScheme(key.publicKey)
        } catch (e: Throwable) {
            fail("The public key (algorithm=${key.publicKey.algorithm}) must be recognisable by scheme metadata.")
        }
        assertEquals(expected, keyScheme, "The public key scheme must match ${expected.codeName}")
        when (key) {
            is GeneratedWrappedKey -> {
                assertTrue(key.keyMaterial.isNotEmpty(), "The generated key must have keyMaterial.")
            }
            is GeneratedPublicKey -> {
                assertTrue(key.hsmAlias.isNotBlank(), "The alias generated by HSM must not be blank.")
                if (!alias.isNullOrBlank()) {
                    assertNotEquals(key.hsmAlias, alias, "The alias used by HSM must not match the passed.")
                }
            }
            else -> {
                fail("Unexpected type of the generated key '${key::class.java.name}'")
            }
        }
    }

    class Experiment(
        val key: GeneratedKey,
        val keyScheme: KeyScheme,
        val signatureSpec: SignatureSpec,
        val clearData: ByteArray,
        val signature: ByteArray
    )
}