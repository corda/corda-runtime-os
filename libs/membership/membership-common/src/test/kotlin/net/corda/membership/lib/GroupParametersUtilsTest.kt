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
    companion object {
        const val DUMMY_PROP_KEY_1 = "prop-key-1"
        const val DUMMY_PROP_VALUE_1 = "prop-value-1"
        const val DUMMY_PROP_KEY_2 = "prop-key-2"
        const val DUMMY_PROP_VALUE_2 = "prop-value-2"
    }

    private val mockSignedBytes = byteArrayOf(1, 2, 3)
    private val mockSignature = DigitalSignature.WithKey(mock(), byteArrayOf(4, 5, 6), emptyMap())

    private fun mockGroupParameters(
        paramMap: Map<String, String> = mapOf(DUMMY_PROP_KEY_1 to DUMMY_PROP_VALUE_1),
        signed: Boolean = false
    ): GroupParameters = if (signed) {
        mock<SignedGroupParameters> {
            on { entries } doReturn paramMap.entries
            on { bytes } doReturn mockSignedBytes
            on { signature } doReturn mockSignature
        }
    } else {
        mock {
            on { entries } doReturn paramMap.entries
        }
    }

    @Nested
    inner class HashTest {
        @Test
        fun `hash can be calculated for group parameters`() {
            val result = assertDoesNotThrow { mockGroupParameters().hash }

            assertThat(result).isInstanceOf(SecureHash::class.java)
            assertThat(result.algorithm).isEqualTo(DigestAlgorithmName.SHA2_256.name)
            assertThat(result.bytes.size).isGreaterThan(0)
        }

        @Test
        fun `same entries for different group parameters objects produce the same hash`() {
            val params1 = mockGroupParameters()
            val params2 = mockGroupParameters()

            assertThat(params1.hash).isEqualTo(params2.hash)
            assertThat(params1).isNotSameAs(params2)
            assertThat(params1.entries)
                .isNotSameAs(params2.entries)
                .isEqualTo(params2.entries)
        }

        @Test
        fun `group parameters with different entries have a different hash`() {
            val params1 = mockGroupParameters()
            val params2 = mockGroupParameters(mapOf(DUMMY_PROP_KEY_2 to DUMMY_PROP_VALUE_2))

            assertThat(params1.hash).isNotEqualTo(params2.hash)
            assertThat(params1.entries)
                .isNotEqualTo(params2.entries)
        }

        @Test
        fun `group parameters with same entries in a different order have the same hash`() {
            val params1 = mockGroupParameters(
                mapOf(
                    DUMMY_PROP_KEY_1 to DUMMY_PROP_VALUE_1,
                    DUMMY_PROP_KEY_2 to DUMMY_PROP_VALUE_2
                )
            )
            val params2 = mockGroupParameters(
                mapOf(
                    DUMMY_PROP_KEY_2 to DUMMY_PROP_VALUE_2,
                    DUMMY_PROP_KEY_1 to DUMMY_PROP_VALUE_1
                )
            )

            assertThat(params1.hash).isEqualTo(params2.hash)
        }
    }

    @Nested
    inner class SignedBytesTest {
        @Test
        fun `unsigned group parameters return null for the signed bytes`() {
            assertThat(mockGroupParameters().bytes).isNull()
        }

        @Test
        fun `signed group parameters returns the signed bytes`() {
            assertThat(mockGroupParameters(signed = true).bytes).isNotNull.isEqualTo(mockSignedBytes)
        }
    }

    @Nested
    inner class SignatureTest {
        @Test
        fun `unsigned group parameters return null for the signature`() {
            assertThat(mockGroupParameters().signature).isNull()
        }

        @Test
        fun `signed group parameters returns the signed bytes`() {
            assertThat(mockGroupParameters(signed = true).signature)
                .isNotNull
                .isEqualTo(mockSignature)
        }
    }
}