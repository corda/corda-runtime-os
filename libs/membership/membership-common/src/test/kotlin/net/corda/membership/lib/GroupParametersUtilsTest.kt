package net.corda.membership.lib

import net.corda.v5.crypto.DigestAlgorithmName
import net.corda.v5.crypto.DigitalSignature
import net.corda.v5.crypto.SecureHash
import net.corda.v5.membership.GroupParameters
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock

class GroupParametersUtilsTest {

    private val mockSignature = DigitalSignature.WithKey(mock(), byteArrayOf(4, 5, 6), emptyMap())

    private fun mockGroupParameters(
        serializedBytes: ByteArray = "group-params-a".toByteArray(),
        signed: Boolean = false
    ): GroupParameters = if (signed) {
        mock<SignedGroupParameters> {
            on { bytes } doReturn serializedBytes
            on { signature } doReturn mockSignature
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

        @Test
        fun `same serialized bytes for different group parameters objects produce the same hash`() {
            val params1 = mockGroupParameters()
            val params2 = mockGroupParameters()

            assertThat(params1.hash()).isEqualTo(params2.hash())
            assertThat(params1).isNotSameAs(params2)
            assertThat(params1.bytes)
                .isNotSameAs(params2.bytes)
                .isEqualTo(params2.bytes)
        }

        @Test
        fun `group parameters with different serialized bytes have a different hash`() {
            val params1 = mockGroupParameters()
            val params2 = mockGroupParameters("group-params-2".toByteArray())

            assertThat(params1.hash()).isNotEqualTo(params2.hash())
            assertThat(params1.bytes)
                .isNotEqualTo(params2.bytes)
        }
    }
}