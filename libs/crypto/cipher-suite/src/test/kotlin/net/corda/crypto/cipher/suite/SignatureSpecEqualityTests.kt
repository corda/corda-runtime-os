package net.corda.crypto.cipher.suite

import net.corda.v5.crypto.DigestAlgorithmName
import net.corda.v5.crypto.ParameterizedSignatureSpec
import net.corda.v5.crypto.SignatureSpec
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import java.security.spec.MGF1ParameterSpec
import java.security.spec.PSSParameterSpec
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SignatureSpecEqualityTests {
    companion object {
        private val SIGNATURE_SPEC_1 = SignatureSpec("SHA256withRSA")
        private val SIGNATURE_SPEC_10 = SignatureSpec("SHA256withRSA")
        private val SIGNATURE_SPEC_100 = SignatureSpec("sha256WITHrsa")
        private val SIGNATURE_SPEC_2 = SignatureSpec("SHA384withRSA")
        private val PARAMETERIZED_SIGNATURE_SPEC_1 = ParameterizedSignatureSpec(
            "RSASSA-PSS",
            PSSParameterSpec(
                "SHA-256",
                "MGF1",
                MGF1ParameterSpec.SHA256,
                32,
                1
            )
        )
        private val PARAMETERIZED_SIGNATURE_SPEC_10 = ParameterizedSignatureSpec(
            "RSASSA-PSS",
            PSSParameterSpec(
                "SHA-256",
                "MGF1",
                MGF1ParameterSpec.SHA256,
                32,
                1
            )
        )
        private val PARAMETERIZED_SIGNATURE_SPEC_100 = ParameterizedSignatureSpec(
            "rsassa-pss",
            PSSParameterSpec(
                "sha-256",
                "mgf1",
                MGF1ParameterSpec("sha-256"),
                32,
                1
            )
        )
        private val PARAMETERIZED_SIGNATURE_SPEC_2 = ParameterizedSignatureSpec(
            "RSASSA-PSS",
            PSSParameterSpec(
                "SHA-384",
                "MGF1",
                MGF1ParameterSpec.SHA384,
                48,
                1
            )
        )
        private val PARAMETERIZED_SIGNATURE_SPEC_3 = ParameterizedSignatureSpec(
            "RSASSA-PSS-?",
            PSSParameterSpec(
                "SHA-256",
                "MGF1",
                MGF1ParameterSpec.SHA256,
                32,
                1
            )
        )
        private val PARAMETERIZED_SIGNATURE_SPEC_4 = ParameterizedSignatureSpec(
            "RSASSA-PSS",
            mock()
        )
        private val PARAMETERIZED_SIGNATURE_SPEC_5 = ParameterizedSignatureSpec(
            "RSASSA-PSS-?",
            PSSParameterSpec(
                "SHA-256",
                "MGF1",
                mock(),
                32,
                1
            )
        )
        private val CUSTOM_SIGNATURE_SPEC_1 = CustomSignatureSpec(
            "RSA/NONE/PKCS1Padding",
            DigestAlgorithmName.SHA2_256
        )
        private val CUSTOM_SIGNATURE_SPEC_10 = CustomSignatureSpec(
            "RSA/NONE/PKCS1Padding",
            DigestAlgorithmName.SHA2_256
        )
        private val CUSTOM_SIGNATURE_SPEC_100 = CustomSignatureSpec(
            "rsa/NONE/pkcs1Padding",
            DigestAlgorithmName("sha-256")
        )
        private val CUSTOM_SIGNATURE_SPEC_2 = CustomSignatureSpec(
            "RSA/NONE/PKCS1Padding",
            DigestAlgorithmName.SHA2_384
        )
        private val CUSTOM_SIGNATURE_SPEC_3 = CustomSignatureSpec(
            "RSA/NONE/PKCS1Padding",
            DigestAlgorithmName.SHA2_256,
            PSSParameterSpec(
                "SHA-256",
                "MGF1",
                MGF1ParameterSpec.SHA256,
                48,
                1
            )
        )
        private val CUSTOM_SIGNATURE_SPEC_30 = CustomSignatureSpec(
            "RSA/NONE/PKCS1Padding",
            DigestAlgorithmName.SHA2_256,
            PSSParameterSpec(
                "SHA-256",
                "MGF1",
                MGF1ParameterSpec.SHA256,
                48,
                1
            )
        )
        private val CUSTOM_SIGNATURE_SPEC_300 = CustomSignatureSpec(
            "RSA/none/PKCS1PADDING",
            DigestAlgorithmName("sha-256"),
            PSSParameterSpec(
                "sha-256",
                "MGF1",
                MGF1ParameterSpec("sha-256"),
                48,
                1
            )
        )
        private val CUSTOM_SIGNATURE_SPEC_4 = CustomSignatureSpec(
            "RSA/NONE/PKCS1Padding",
            DigestAlgorithmName.SHA2_384,
            PSSParameterSpec(
                "SHA-384",
                "MGF1",
                MGF1ParameterSpec.SHA384,
                48,
                1
            )
        )
        private val CUSTOM_SIGNATURE_SPEC_5 = CustomSignatureSpec(
            "RSA/NONE/PKCS1Padding",
            DigestAlgorithmName.SHA2_384,
            mock()
        )
        private val CUSTOM_SIGNATURE_SPEC_6 = CustomSignatureSpec(
            "RSA/NONE/PKCS1Padding",
            DigestAlgorithmName.SHA2_384,
            PSSParameterSpec(
                "SHA-384",
                "MGF1",
                mock(),
                48,
                1
            )
        )
    }

    @Test
    fun `Should be equal when comparing to itself`() {
        assertTrue(SIGNATURE_SPEC_1.equal(SIGNATURE_SPEC_1))
        assertTrue(PARAMETERIZED_SIGNATURE_SPEC_1.equal(PARAMETERIZED_SIGNATURE_SPEC_1))
        assertTrue(PARAMETERIZED_SIGNATURE_SPEC_4.equal(PARAMETERIZED_SIGNATURE_SPEC_4))
        assertTrue(CUSTOM_SIGNATURE_SPEC_1.equal(CUSTOM_SIGNATURE_SPEC_1))
    }

    @Test
    fun `Should be equal when comparing equal objects`() {
        assertTrue(SIGNATURE_SPEC_1.equal(SIGNATURE_SPEC_10))
        assertTrue(SIGNATURE_SPEC_10.equal(SIGNATURE_SPEC_1))
        assertTrue(PARAMETERIZED_SIGNATURE_SPEC_1.equal(PARAMETERIZED_SIGNATURE_SPEC_10))
        assertTrue(PARAMETERIZED_SIGNATURE_SPEC_10.equal(PARAMETERIZED_SIGNATURE_SPEC_1))
        assertTrue(CUSTOM_SIGNATURE_SPEC_1.equal(CUSTOM_SIGNATURE_SPEC_10))
        assertTrue(CUSTOM_SIGNATURE_SPEC_10.equal(CUSTOM_SIGNATURE_SPEC_1))
        assertTrue(CUSTOM_SIGNATURE_SPEC_3.equal(CUSTOM_SIGNATURE_SPEC_30))
        assertTrue(CUSTOM_SIGNATURE_SPEC_30.equal(CUSTOM_SIGNATURE_SPEC_3))
    }

    @Test
    fun `Should be equal when comparing equal objects regardless string casing`() {
        assertTrue(SIGNATURE_SPEC_1.equal(SIGNATURE_SPEC_100))
        assertTrue(SIGNATURE_SPEC_100.equal(SIGNATURE_SPEC_1))
        assertTrue(PARAMETERIZED_SIGNATURE_SPEC_1.equal(PARAMETERIZED_SIGNATURE_SPEC_100))
        assertTrue(PARAMETERIZED_SIGNATURE_SPEC_100.equal(PARAMETERIZED_SIGNATURE_SPEC_1))
        assertTrue(CUSTOM_SIGNATURE_SPEC_1.equal(CUSTOM_SIGNATURE_SPEC_100))
        assertTrue(CUSTOM_SIGNATURE_SPEC_100.equal(CUSTOM_SIGNATURE_SPEC_1))
        assertTrue(CUSTOM_SIGNATURE_SPEC_3.equal(CUSTOM_SIGNATURE_SPEC_300))
        assertTrue(CUSTOM_SIGNATURE_SPEC_300.equal(CUSTOM_SIGNATURE_SPEC_3))
    }

    @Test
    fun `Should be equal when comparing two null values`() {
        val spec: SignatureSpec? = null
        assertTrue(spec.equal(null))
    }

    @Test
    fun `Should be not equal when comparing to null`() {
        assertFalse(SIGNATURE_SPEC_1.equal(null))
        assertFalse(PARAMETERIZED_SIGNATURE_SPEC_1.equal(null))
        assertFalse(CUSTOM_SIGNATURE_SPEC_1.equal(null))
    }

    @Test
    fun `Should be not equal when comparing to different type`() {
        assertFalse(SIGNATURE_SPEC_1.equal(PARAMETERIZED_SIGNATURE_SPEC_1))
        assertFalse(SIGNATURE_SPEC_1.equal(CUSTOM_SIGNATURE_SPEC_1))
        assertFalse(PARAMETERIZED_SIGNATURE_SPEC_1.equal(SIGNATURE_SPEC_1))
        assertFalse(PARAMETERIZED_SIGNATURE_SPEC_1.equal(CUSTOM_SIGNATURE_SPEC_1))
        assertFalse(CUSTOM_SIGNATURE_SPEC_1.equal(SIGNATURE_SPEC_1))
        assertFalse(CUSTOM_SIGNATURE_SPEC_1.equal(PARAMETERIZED_SIGNATURE_SPEC_1))
    }

    @Test
    fun `Should be not equal when comparing non equal objects of the same type`() {
        assertFalse(SIGNATURE_SPEC_1.equal(SIGNATURE_SPEC_2))
        assertFalse(PARAMETERIZED_SIGNATURE_SPEC_1.equal(PARAMETERIZED_SIGNATURE_SPEC_2))
        assertFalse(PARAMETERIZED_SIGNATURE_SPEC_1.equal(PARAMETERIZED_SIGNATURE_SPEC_3))
        assertFalse(PARAMETERIZED_SIGNATURE_SPEC_1.equal(PARAMETERIZED_SIGNATURE_SPEC_4))
        assertFalse(PARAMETERIZED_SIGNATURE_SPEC_1.equal(PARAMETERIZED_SIGNATURE_SPEC_5))
        assertFalse(PARAMETERIZED_SIGNATURE_SPEC_2.equal(PARAMETERIZED_SIGNATURE_SPEC_1))
        assertFalse(PARAMETERIZED_SIGNATURE_SPEC_2.equal(PARAMETERIZED_SIGNATURE_SPEC_3))
        assertFalse(PARAMETERIZED_SIGNATURE_SPEC_2.equal(PARAMETERIZED_SIGNATURE_SPEC_4))
        assertFalse(PARAMETERIZED_SIGNATURE_SPEC_2.equal(PARAMETERIZED_SIGNATURE_SPEC_5))
        assertFalse(PARAMETERIZED_SIGNATURE_SPEC_3.equal(PARAMETERIZED_SIGNATURE_SPEC_1))
        assertFalse(PARAMETERIZED_SIGNATURE_SPEC_3.equal(PARAMETERIZED_SIGNATURE_SPEC_2))
        assertFalse(PARAMETERIZED_SIGNATURE_SPEC_3.equal(PARAMETERIZED_SIGNATURE_SPEC_4))
        assertFalse(PARAMETERIZED_SIGNATURE_SPEC_3.equal(PARAMETERIZED_SIGNATURE_SPEC_5))
        assertFalse(PARAMETERIZED_SIGNATURE_SPEC_4.equal(PARAMETERIZED_SIGNATURE_SPEC_1))
        assertFalse(PARAMETERIZED_SIGNATURE_SPEC_4.equal(PARAMETERIZED_SIGNATURE_SPEC_2))
        assertFalse(PARAMETERIZED_SIGNATURE_SPEC_4.equal(PARAMETERIZED_SIGNATURE_SPEC_3))
        assertFalse(PARAMETERIZED_SIGNATURE_SPEC_4.equal(PARAMETERIZED_SIGNATURE_SPEC_5))
        assertFalse(PARAMETERIZED_SIGNATURE_SPEC_5.equal(PARAMETERIZED_SIGNATURE_SPEC_1))
        assertFalse(PARAMETERIZED_SIGNATURE_SPEC_5.equal(PARAMETERIZED_SIGNATURE_SPEC_2))
        assertFalse(PARAMETERIZED_SIGNATURE_SPEC_5.equal(PARAMETERIZED_SIGNATURE_SPEC_3))
        assertFalse(PARAMETERIZED_SIGNATURE_SPEC_5.equal(PARAMETERIZED_SIGNATURE_SPEC_4))
        assertFalse(CUSTOM_SIGNATURE_SPEC_1.equal(CUSTOM_SIGNATURE_SPEC_2))
        assertFalse(CUSTOM_SIGNATURE_SPEC_1.equal(CUSTOM_SIGNATURE_SPEC_3))
        assertFalse(CUSTOM_SIGNATURE_SPEC_1.equal(CUSTOM_SIGNATURE_SPEC_4))
        assertFalse(CUSTOM_SIGNATURE_SPEC_1.equal(CUSTOM_SIGNATURE_SPEC_5))
        assertFalse(CUSTOM_SIGNATURE_SPEC_1.equal(CUSTOM_SIGNATURE_SPEC_6))
        assertFalse(CUSTOM_SIGNATURE_SPEC_2.equal(CUSTOM_SIGNATURE_SPEC_1))
        assertFalse(CUSTOM_SIGNATURE_SPEC_2.equal(CUSTOM_SIGNATURE_SPEC_3))
        assertFalse(CUSTOM_SIGNATURE_SPEC_2.equal(CUSTOM_SIGNATURE_SPEC_4))
        assertFalse(CUSTOM_SIGNATURE_SPEC_2.equal(CUSTOM_SIGNATURE_SPEC_5))
        assertFalse(CUSTOM_SIGNATURE_SPEC_2.equal(CUSTOM_SIGNATURE_SPEC_6))
        assertFalse(CUSTOM_SIGNATURE_SPEC_3.equal(CUSTOM_SIGNATURE_SPEC_1))
        assertFalse(CUSTOM_SIGNATURE_SPEC_3.equal(CUSTOM_SIGNATURE_SPEC_2))
        assertFalse(CUSTOM_SIGNATURE_SPEC_3.equal(CUSTOM_SIGNATURE_SPEC_4))
        assertFalse(CUSTOM_SIGNATURE_SPEC_3.equal(CUSTOM_SIGNATURE_SPEC_5))
        assertFalse(CUSTOM_SIGNATURE_SPEC_3.equal(CUSTOM_SIGNATURE_SPEC_6))
        assertFalse(CUSTOM_SIGNATURE_SPEC_4.equal(CUSTOM_SIGNATURE_SPEC_1))
        assertFalse(CUSTOM_SIGNATURE_SPEC_4.equal(CUSTOM_SIGNATURE_SPEC_2))
        assertFalse(CUSTOM_SIGNATURE_SPEC_4.equal(CUSTOM_SIGNATURE_SPEC_3))
        assertFalse(CUSTOM_SIGNATURE_SPEC_4.equal(CUSTOM_SIGNATURE_SPEC_5))
        assertFalse(CUSTOM_SIGNATURE_SPEC_4.equal(CUSTOM_SIGNATURE_SPEC_6))
        assertFalse(CUSTOM_SIGNATURE_SPEC_5.equal(CUSTOM_SIGNATURE_SPEC_1))
        assertFalse(CUSTOM_SIGNATURE_SPEC_5.equal(CUSTOM_SIGNATURE_SPEC_2))
        assertFalse(CUSTOM_SIGNATURE_SPEC_5.equal(CUSTOM_SIGNATURE_SPEC_3))
        assertFalse(CUSTOM_SIGNATURE_SPEC_5.equal(CUSTOM_SIGNATURE_SPEC_4))
        assertFalse(CUSTOM_SIGNATURE_SPEC_5.equal(CUSTOM_SIGNATURE_SPEC_6))
        assertFalse(CUSTOM_SIGNATURE_SPEC_6.equal(CUSTOM_SIGNATURE_SPEC_1))
        assertFalse(CUSTOM_SIGNATURE_SPEC_6.equal(CUSTOM_SIGNATURE_SPEC_2))
        assertFalse(CUSTOM_SIGNATURE_SPEC_6.equal(CUSTOM_SIGNATURE_SPEC_3))
        assertFalse(CUSTOM_SIGNATURE_SPEC_6.equal(CUSTOM_SIGNATURE_SPEC_4))
        assertFalse(CUSTOM_SIGNATURE_SPEC_6.equal(CUSTOM_SIGNATURE_SPEC_5))
    }
}