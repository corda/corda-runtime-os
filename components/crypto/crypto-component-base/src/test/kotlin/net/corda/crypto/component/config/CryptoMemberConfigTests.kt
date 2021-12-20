package net.corda.crypto.component.config

import net.corda.crypto.CryptoConsts
import net.corda.v5.cipher.suite.config.CryptoServiceConfig
import net.corda.v5.cipher.suite.schemes.ECDSA_SECP256R1_CODE_NAME
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.time.Duration
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CryptoMemberConfigTests {
    @Test
    @Timeout(5)
    fun `Should return configured values for requested category`() {
        val config = CryptoMemberConfigImpl(
            mapOf(
                "default" to mapOf(
                    "serviceName" to "default",
                    "timeout" to "1",
                    "defaultSignatureScheme" to "CORDA.ECDSA.SECP256K1",
                    "serviceConfig" to mapOf(
                        "passphrase" to "pwdD",
                        "salt" to "saltD"
                    )
                ),
                "LEDGER" to mapOf(
                    "serviceName" to "FUTUREX",
                    "timeout" to "10",
                    "defaultSignatureScheme" to "CORDA.EDDSA.ED25519",
                    "serviceConfig" to mapOf(
                        "credentials" to "password"
                    )
                )
            )
        )
        val category = config.getCategory(CryptoConsts.CryptoCategories.LEDGER)
        assertEquals("FUTUREX", category.serviceName)
        assertEquals(Duration.ofSeconds(10), category.timeout)
        assertEquals("CORDA.EDDSA.ED25519", category.defaultSignatureScheme)
        assertEquals(1, category.serviceConfig.size)
        assertEquals("password", category.serviceConfig["credentials"])
    }

    @Test
    @Timeout(5)
    fun `Should return default category values when requested category is not defined`() {
        val config = CryptoMemberConfigImpl(
            mapOf(
                "default" to mapOf(
                    "serviceName" to "default",
                    "timeout" to "1",
                    "defaultSignatureScheme" to "CORDA.ECDSA.SECP256K1",
                    "serviceConfig" to mapOf(
                        "passphrase" to "pwdD",
                        "salt" to "saltD"
                    )
                ),
                "LEDGER" to mapOf(
                    "serviceName" to "FUTUREX",
                    "timeout" to "10",
                    "defaultSignatureScheme" to "CORDA.EDDSA.ED25519",
                    "serviceConfig" to mapOf(
                        "credentials" to "password"
                    )
                )
            )
        )
        val category = config.getCategory(CryptoConsts.CryptoCategories.FRESH_KEYS)
        assertEquals("default", category.serviceName)
        assertEquals(Duration.ofSeconds(1), category.timeout)
        assertEquals("CORDA.ECDSA.SECP256K1", category.defaultSignatureScheme)
        assertEquals(2, category.serviceConfig.size)
        assertEquals("pwdD", category.serviceConfig["passphrase"])
        assertEquals("saltD", category.serviceConfig["salt"])
    }

    @Test
    @Timeout(5)
    fun `Should use default values for get category when no data is supplied`() {
        val config = CryptoMemberConfigImpl(emptyMap())
        val category = config.getCategory(CryptoConsts.CryptoCategories.LEDGER)
        assertEquals(CryptoServiceConfig.DEFAULT_SERVICE_NAME, category.serviceName)
        assertEquals(Duration.ofSeconds(5), category.timeout)
        assertEquals(ECDSA_SECP256R1_CODE_NAME, category.defaultSignatureScheme)
        assertTrue(category.serviceConfig.isEmpty())
    }

    @Test
    @Timeout(5)
    fun `Should use default values for default category configuration when no data is supplied`() {
        val config = CryptoMemberConfigImpl(emptyMap())
        assertEquals(CryptoServiceConfig.DEFAULT_SERVICE_NAME, config.default.serviceName)
        assertEquals(Duration.ofSeconds(5), config.default.timeout)
        assertEquals(ECDSA_SECP256R1_CODE_NAME, config.default.defaultSignatureScheme)
        assertTrue(config.default.serviceConfig.isEmpty())
    }
}