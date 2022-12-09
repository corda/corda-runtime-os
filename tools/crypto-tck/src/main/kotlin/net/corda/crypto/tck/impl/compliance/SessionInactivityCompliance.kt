package net.corda.crypto.tck.impl.compliance

import net.corda.crypto.cipher.suite.GeneratedKey
import net.corda.crypto.cipher.suite.GeneratedPublicKey
import net.corda.crypto.tck.impl.ComplianceSpec
import net.corda.crypto.tck.impl.ComplianceSpecExtension
import net.corda.crypto.tck.impl.CryptoServiceProviderMap
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.osgi.test.common.annotation.InjectService
import org.osgi.test.junit5.service.ServiceExtension

@ExtendWith(ServiceExtension::class, ComplianceSpecExtension::class)
class SessionInactivityCompliance : AbstractCompliance() {
    companion object {
        @InjectService(timeout = 10000L)
        lateinit var providers: CryptoServiceProviderMap
    }

    @BeforeEach
    fun setup(spec: ComplianceSpec) {
        super.setup(spec, providers)
    }

    @AfterEach
    fun cleanup() {
        if (masterKeyAlias != null) {
            deleteWrappingKey(masterKeyAlias!!)
        }
    }

    @Test
    fun `Should be able to sign after specified session timeout`() {
        logger.info(
            "scheme=${compliance.options.sessionComplianceSpec!!.first}," +
                    "signatureSpec=${compliance.options.sessionComplianceSpec!!.second.signatureName}"
        )
        val keyScheme = CryptoServiceCompliance.schemeMetadata.findKeyScheme(
            compliance.options.sessionComplianceSpec!!.first
        )
        val alias = compliance.generateRandomIdentifier()
        var key: GeneratedKey? = null
        try {
            key = `Should generate key with expected key scheme`(alias, masterKeyAlias, keyScheme)
            logger.info("Sleeping for ${compliance.options.sessionComplianceTimeout}")
            Thread.sleep(compliance.options.sessionComplianceTimeout.toMillis())
            logger.info("Woke up after ${compliance.options.sessionComplianceTimeout}")
            `Should be able to sign byte arrays of different lengths`(
                key,
                keyScheme,
                compliance.options.sessionComplianceSpec!!.second
            )
        } finally {
            cleanupKeyPair(key)
        }
    }

    @Test
    fun `Should be able to generate key pair with suggested alias again after specified session timeout`() {
        logger.info(
            "scheme=${compliance.options.sessionComplianceSpec!!.first}," +
                    "signatureSpec=${compliance.options.sessionComplianceSpec!!.second.signatureName}"
        )
        val keyScheme = CryptoServiceCompliance.schemeMetadata.findKeyScheme(
            compliance.options.sessionComplianceSpec!!.first
        )
        var key1: GeneratedKey? = null
        var key2: GeneratedKey? = null
        try {
            val alias = compliance.generateRandomIdentifier()
            key1 = `Should generate key with expected key scheme`(alias, masterKeyAlias, keyScheme)
            logger.info("Sleeping for ${compliance.options.sessionComplianceTimeout}")
            Thread.sleep(compliance.options.sessionComplianceTimeout.toMillis())
            logger.info("Woke up after ${compliance.options.sessionComplianceTimeout}")
            key2 = `Should generate key with expected key scheme`(alias, masterKeyAlias, keyScheme)
            assertNotEquals(key1.publicKey, key2.publicKey, "Generated keys must be distinct.")
        } finally {
            cleanupKeyPair(key1)
            cleanupKeyPair(key2)
        }
    }

    @Test
    @Suppress("MaxLineLength")
    fun `Should be able to generate key pair without suggested alias, suggesting wrapped key, again after specified session timeout`() {
        logger.info(
            "scheme=${compliance.options.sessionComplianceSpec!!.first}," +
                    "signatureSpec=${compliance.options.sessionComplianceSpec!!.second.signatureName}"
        )
        val keyScheme = CryptoServiceCompliance.schemeMetadata.findKeyScheme(
            compliance.options.sessionComplianceSpec!!.first
        )
        var key1: GeneratedKey? = null
        var key2: GeneratedKey? = null
        try {
            key1 = `Should generate key with expected key scheme`(null, masterKeyAlias, keyScheme)
            logger.info("Sleeping for ${compliance.options.sessionComplianceTimeout}")
            Thread.sleep(compliance.options.sessionComplianceTimeout.toMillis())
            logger.info("Woke up after ${compliance.options.sessionComplianceTimeout}")
            key2 = `Should generate key with expected key scheme`(null, masterKeyAlias, keyScheme)
            assertNotEquals(key1.publicKey, key2.publicKey, "Generated keys must be distinct.")
        } finally {
            cleanupKeyPair(key1)
            cleanupKeyPair(key2)
        }
    }

    private fun cleanupKeyPair(key: GeneratedKey?) {
        if (key is GeneratedPublicKey && key.hsmAlias.isNotBlank()) {
            deleteKeyPair(key.hsmAlias)
        }
    }
}