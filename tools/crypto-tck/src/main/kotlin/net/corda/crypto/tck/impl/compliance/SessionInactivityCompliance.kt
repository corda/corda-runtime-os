package net.corda.crypto.tck.impl.compliance

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
    fun clenup() {
        cleanupKeys()
    }

    @Test
    fun `Should be able to sign after specified session timeout`() {
        logger.info("scheme=${compliance.options.sessionComplianceSpec!!.first}," +
                "signatureSpec=${compliance.options.sessionComplianceSpec!!.second.signatureName}")
        val keyScheme = CryptoServiceCompliance.schemeMetadata.findKeyScheme(
            compliance.options.sessionComplianceSpec!!.first
        )
        val alias = compliance.generateRandomIdentifier()
        val key = `Should generate key with expected key scheme`(alias, masterKeyAlias, keyScheme)
        logger.info("Sleeping for ${compliance.options.sessionComplianceTimeout}")
        Thread.sleep(compliance.options.sessionComplianceTimeout.toMillis())
        logger.info("Woke up after ${compliance.options.sessionComplianceTimeout}")
        `Should be able to sign and verify signature`(
            key,
            keyScheme,
            compliance.options.sessionComplianceSpec!!.second
        )
    }

    @Test
    fun `Should be able to generate key pair with suggested alias again after specified session timeout`() {
        logger.info("scheme=${compliance.options.sessionComplianceSpec!!.first}," +
                "signatureSpec=${compliance.options.sessionComplianceSpec!!.second.signatureName}")
        val keyScheme = CryptoServiceCompliance.schemeMetadata.findKeyScheme(
            compliance.options.sessionComplianceSpec!!.first
        )
        val alias = compliance.generateRandomIdentifier()
        val key1 = `Should generate key with expected key scheme`(alias, masterKeyAlias, keyScheme)
        logger.info("Sleeping for ${compliance.options.sessionComplianceTimeout}")
        Thread.sleep(compliance.options.sessionComplianceTimeout.toMillis())
        logger.info("Woke up after ${compliance.options.sessionComplianceTimeout}")
        val key2 = `Should generate key with expected key scheme`(alias, masterKeyAlias, keyScheme)
        assertNotEquals(key1.publicKey, key2.publicKey, "Generated keys must be distinct.")
    }

    @Test
    @Suppress("MaxLineLength")
    fun `Should be able to generate key pair without suggested alias, suggesting wrapped key, again after specified session timeout`() {
        logger.info("scheme=${compliance.options.sessionComplianceSpec!!.first}," +
                "signatureSpec=${compliance.options.sessionComplianceSpec!!.second.signatureName}")
        val keyScheme = CryptoServiceCompliance.schemeMetadata.findKeyScheme(
            compliance.options.sessionComplianceSpec!!.first
        )
        val key1 = `Should generate key with expected key scheme`(null, masterKeyAlias, keyScheme)
        logger.info("Sleeping for ${compliance.options.sessionComplianceTimeout}")
        Thread.sleep(compliance.options.sessionComplianceTimeout.toMillis())
        logger.info("Woke up after ${compliance.options.sessionComplianceTimeout}")
        val key2 = `Should generate key with expected key scheme`(null, masterKeyAlias, keyScheme)
        assertNotEquals(key1.publicKey, key2.publicKey, "Generated keys must be distinct.")
    }
}