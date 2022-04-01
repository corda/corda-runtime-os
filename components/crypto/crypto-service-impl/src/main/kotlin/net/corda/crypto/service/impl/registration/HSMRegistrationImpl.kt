package net.corda.crypto.service.impl.registration

import com.fasterxml.jackson.databind.ObjectMapper
import net.corda.crypto.core.CryptoConsts
import net.corda.crypto.service.SoftCryptoServiceConfig
import net.corda.crypto.service.impl.soft.SoftCryptoServiceProviderImpl
import net.corda.data.crypto.config.HSMConfig
import net.corda.data.crypto.config.HSMInfo
import net.corda.data.crypto.config.TenantHSMConfig
import net.corda.v5.cipher.suite.schemes.ECDSA_SECP256K1_CODE_NAME
import net.corda.v5.cipher.suite.schemes.ECDSA_SECP256R1_CODE_NAME
import net.corda.v5.cipher.suite.schemes.EDDSA_ED25519_CODE_NAME
import net.corda.v5.cipher.suite.schemes.GOST3410_GOST3411_CODE_NAME
import net.corda.v5.cipher.suite.schemes.RSA_CODE_NAME
import net.corda.v5.cipher.suite.schemes.SM2_CODE_NAME
import net.corda.v5.cipher.suite.schemes.SPHINCS256_CODE_NAME
import org.osgi.service.component.annotations.Component
import java.nio.ByteBuffer
import java.time.Instant

@Component(service = [HSMRegistration::class])
class HSMRegistrationImpl : HSMRegistration {
    companion object {
        private val serializer = ObjectMapper()
    }

    override fun getHSMConfig(id: String): HSMConfig {
        require(id.startsWith("dummy-")) {
            "Currently supports only hardcoded Soft HSM configuration with ids having 'dummy-' prefix."
        }
        return HSMConfig(
            HSMInfo(
                "dummy",
                Instant.now(),
                1,
                "default",
                "Dummy configuration",
                SoftCryptoServiceProviderImpl.SERVICE_NAME,
                null,
                listOf(
                    CryptoConsts.HsmCategories.FRESH_KEYS,
                    CryptoConsts.HsmCategories.LEDGER,
                    CryptoConsts.HsmCategories.NOTARY,
                    CryptoConsts.HsmCategories.SESSION,
                    CryptoConsts.HsmCategories.TLS
                ),
                0,
                5000,
                listOf(
                    RSA_CODE_NAME,
                    ECDSA_SECP256K1_CODE_NAME,
                    ECDSA_SECP256R1_CODE_NAME,
                    EDDSA_ED25519_CODE_NAME,
                    SPHINCS256_CODE_NAME,
                    SM2_CODE_NAME,
                    GOST3410_GOST3411_CODE_NAME
                )
            ),
            ByteBuffer.wrap(
                serializer.writeValueAsBytes(
                    SoftCryptoServiceConfig(
                        passphrase = "PASSPHRASE",
                        salt = "SALT"
                    )
                )
            )
        )
    }

    override fun getTenantConfig(tenantId: String, category: String): TenantHSMConfig {
        return when(category) {
            CryptoConsts.HsmCategories.TLS -> TenantHSMConfig(
                tenantId,
                "dummy-$category",
                category,
                RSA_CODE_NAME,
                "wrapping-key",
                null
            )
            else -> TenantHSMConfig(
                tenantId,
                "dummy-$category",
                category,
                ECDSA_SECP256R1_CODE_NAME,
                "wrapping-key",
                null
            )
        }
    }

    override val isRunning: Boolean = true

    override fun start() {
    }

    override fun stop() {
    }
}