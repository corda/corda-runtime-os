package net.corda.crypto.tck.testing

import net.corda.crypto.tck.ComplianceTestType
import net.corda.crypto.tck.testing.hsms.AllWrappedKeysHSMProvider
import net.corda.v5.crypto.ECDSA_SECP256R1_CODE_NAME
import net.corda.v5.crypto.EDDSA_ED25519_CODE_NAME
import net.corda.v5.crypto.RSA_CODE_NAME
import net.corda.crypto.tck.CryptoTCK
import net.corda.crypto.tck.testing.hsms.AllAliasedKeysHSMProvider
import net.corda.v5.crypto.ECDSA_SECP256K1_CODE_NAME
import net.corda.v5.crypto.ECDSA_SHA256_SIGNATURE_SPEC
import net.corda.v5.crypto.ECDSA_SHA384_SIGNATURE_SPEC
import net.corda.v5.crypto.EDDSA_ED25519_NONE_SIGNATURE_SPEC
import net.corda.v5.crypto.GOST3410_GOST3411_CODE_NAME
import net.corda.v5.crypto.GOST3410_GOST3411_SIGNATURE_SPEC
import net.corda.v5.crypto.RSASSA_PSS_SHA384_SIGNATURE_SPEC
import net.corda.v5.crypto.RSA_SHA512_SIGNATURE_SPEC
import net.corda.v5.crypto.SM2_CODE_NAME
import net.corda.v5.crypto.SM2_SM3_SIGNATURE_SPEC
import net.corda.v5.crypto.SPHINCS256_CODE_NAME
import net.corda.v5.crypto.SPHINCS256_SHA512_SIGNATURE_SPEC
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.osgi.test.common.annotation.InjectService
import org.osgi.test.junit5.service.ServiceExtension
import java.time.Duration

@ExtendWith(ServiceExtension::class)
class CryptoTCKTests {
    companion object {
        @InjectService(timeout = 5000L)
        lateinit var tck: CryptoTCK
    }

    @Test
    fun `TCK should be able to test AllWrappedKeysHSM`() {
        tck.builder(AllWrappedKeysHSMProvider.NAME, AllWrappedKeysHSMProvider.Configuration("corda"))
            .addScheme(RSA_CODE_NAME, RSA_SHA512_SIGNATURE_SPEC, RSASSA_PSS_SHA384_SIGNATURE_SPEC)
            .addScheme(ECDSA_SECP256R1_CODE_NAME, ECDSA_SHA384_SIGNATURE_SPEC)
            .addScheme(ECDSA_SECP256K1_CODE_NAME, ECDSA_SHA256_SIGNATURE_SPEC)
            .addScheme(EDDSA_ED25519_CODE_NAME, EDDSA_ED25519_NONE_SIGNATURE_SPEC)
            .addScheme(SPHINCS256_CODE_NAME, SPHINCS256_SHA512_SIGNATURE_SPEC)
            .addScheme(SM2_CODE_NAME, SM2_SM3_SIGNATURE_SPEC)
            .addScheme(GOST3410_GOST3411_CODE_NAME, GOST3410_GOST3411_SIGNATURE_SPEC)
            .withServiceTimeout(Duration.ofSeconds(30))
            .withSessionTimeout(Duration.ofSeconds(1))
            .withTestSuites(ComplianceTestType.CRYPTO_SERVICE, ComplianceTestType.SESSION_INACTIVITY)
            .build()
            .run()
    }

    @Test
    fun `TCK should be able to test AllAliasedKeysHSM`() {
        tck.builder(AllAliasedKeysHSMProvider.NAME, AllAliasedKeysHSMProvider.Configuration("corda"))
            .addScheme(RSA_CODE_NAME, RSA_SHA512_SIGNATURE_SPEC, RSASSA_PSS_SHA384_SIGNATURE_SPEC)
            .addScheme(ECDSA_SECP256R1_CODE_NAME, ECDSA_SHA384_SIGNATURE_SPEC)
            .addScheme(EDDSA_ED25519_CODE_NAME, EDDSA_ED25519_NONE_SIGNATURE_SPEC)
            .withServiceTimeout(Duration.ofSeconds(30))
            .withSessionTimeout(Duration.ofSeconds(1))
            .withTestSuites(ComplianceTestType.CRYPTO_SERVICE, ComplianceTestType.SESSION_INACTIVITY)
            .build()
            .run()
    }
}