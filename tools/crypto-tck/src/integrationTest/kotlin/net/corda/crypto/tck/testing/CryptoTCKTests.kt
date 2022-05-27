package net.corda.crypto.tck.testing

import net.corda.crypto.tck.testing.hsms.AllWrappedKeysHSMProvider
import net.corda.v5.crypto.ECDSA_SECP256K1_CODE_NAME
import net.corda.v5.crypto.ECDSA_SECP256R1_CODE_NAME
import net.corda.v5.crypto.EDDSA_ED25519_CODE_NAME
import net.corda.v5.crypto.GOST3410_GOST3411_CODE_NAME
import net.corda.v5.crypto.RSA_CODE_NAME
import net.corda.v5.crypto.SM2_CODE_NAME
import net.corda.v5.crypto.SPHINCS256_CODE_NAME
import net.corda.crypto.tck.CryptoTCK
import net.corda.crypto.tck.ExecutionOptions
import net.corda.v5.crypto.ECDSA_SHA256_SIGNATURE_SPEC
import net.corda.v5.crypto.ECDSA_SHA384_SIGNATURE_SPEC
import net.corda.v5.crypto.ECDSA_SHA512_SIGNATURE_SPEC
import net.corda.v5.crypto.EDDSA_ED25519_NONE_SIGNATURE_SPEC
import net.corda.v5.crypto.GOST3410_GOST3411_SIGNATURE_SPEC
import net.corda.v5.crypto.RSASSA_PSS_SHA256_SIGNATURE_SPEC
import net.corda.v5.crypto.RSASSA_PSS_SHA384_SIGNATURE_SPEC
import net.corda.v5.crypto.RSASSA_PSS_SHA512_SIGNATURE_SPEC
import net.corda.v5.crypto.RSA_SHA256_SIGNATURE_SPEC
import net.corda.v5.crypto.RSA_SHA384_SIGNATURE_SPEC
import net.corda.v5.crypto.RSA_SHA512_SIGNATURE_SPEC
import net.corda.v5.crypto.SM2_SM3_SIGNATURE_SPEC
import net.corda.v5.crypto.SPHINCS256_SHA512_SIGNATURE_SPEC
import net.corda.v5.crypto.SignatureSpec
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.osgi.test.common.annotation.InjectService
import org.osgi.test.junit5.service.ServiceExtension
import java.nio.file.Path


@ExtendWith(ServiceExtension::class)
class CryptoTCKTests {
    companion object {
        @InjectService(timeout = 5000L)
        lateinit var tck: CryptoTCK
    }

    @Test
    fun `TCK should be able to test AllWrappedKeysHSM`() {
        tck.run(
            ExecutionOptions(
                serviceName = AllWrappedKeysHSMProvider.NAME,
                serviceConfig = AllWrappedKeysHSMProvider.Configuration("corda"),
                testResultsDirectory = Path.of("", AllWrappedKeysHSMProvider::class.simpleName).toAbsolutePath(),
                proposedSignatureSpecs = mapOf(
                    RSA_CODE_NAME to listOf(
                        RSA_SHA256_SIGNATURE_SPEC,
                        RSA_SHA384_SIGNATURE_SPEC,
                        RSA_SHA512_SIGNATURE_SPEC,
                        RSASSA_PSS_SHA256_SIGNATURE_SPEC,
                        RSASSA_PSS_SHA384_SIGNATURE_SPEC,
                        RSASSA_PSS_SHA512_SIGNATURE_SPEC
                    ),
                    ECDSA_SECP256K1_CODE_NAME to listOf(
                        ECDSA_SHA256_SIGNATURE_SPEC,
                        ECDSA_SHA384_SIGNATURE_SPEC,
                        ECDSA_SHA512_SIGNATURE_SPEC
                    ),
                    ECDSA_SECP256R1_CODE_NAME to listOf(
                        ECDSA_SHA256_SIGNATURE_SPEC,
                        ECDSA_SHA384_SIGNATURE_SPEC,
                        ECDSA_SHA512_SIGNATURE_SPEC
                    ),
                    EDDSA_ED25519_CODE_NAME to listOf(
                        EDDSA_ED25519_NONE_SIGNATURE_SPEC
                    ),
                    SPHINCS256_CODE_NAME to listOf(
                        SPHINCS256_SHA512_SIGNATURE_SPEC
                    ),
                    SM2_CODE_NAME to listOf(
                        SM2_SM3_SIGNATURE_SPEC,
                        SignatureSpec("SHA256withSM2")
                    ),
                    GOST3410_GOST3411_CODE_NAME to listOf(
                        GOST3410_GOST3411_SIGNATURE_SPEC
                    ),
                )
            )
        )
    }
}