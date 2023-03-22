package net.corda.membership.lib

import net.corda.crypto.core.bytes
import net.corda.v5.crypto.DigestAlgorithmName
import net.corda.v5.crypto.DigitalSignature
import net.corda.v5.crypto.SecureHash
import net.corda.v5.crypto.SignatureSpec
import net.corda.v5.membership.GroupParameters
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock

class GroupParametersUtilsTest {

    private val mockSignature = DigitalSignature.WithKey(mock(), byteArrayOf(4, 5, 6))
    private val mockSignatureSpec = SignatureSpec.ECDSA_SHA256

    private fun mockGroupParameters(
        serializedBytes: ByteArray = "group-params-a".toByteArray(),
        signed: Boolean = false
    ): GroupParameters = if (signed) {
        mock<SignedGroupParameters> {
            on { bytes } doReturn serializedBytes
            on { signature } doReturn mockSignature
            on { signatureSpec } doReturn mockSignatureSpec
        }
    } else {
        mock<UnsignedGroupParameters> {
            on { bytes } doReturn serializedBytes
        }
    }

    @Nested
    inner class HashTest {
        @Test
        fun `hash can be calculated for group parameters`() {
            val result = assertDoesNotThrow { mockGroupParameters().hash() }

            assertThat(result).isInstanceOf(SecureHash::class.java)
            assertThat(result.algorithm).isEqualTo(DigestAlgorithmName.SHA2_256.name)
            assertThat(result.bytes.size).isGreaterThan(0)
        }
    }
}