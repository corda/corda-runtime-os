package net.corda.crypto.service.impl.signing

import net.corda.crypto.core.CryptoConsts
import net.corda.crypto.service.CryptoServiceRef
import net.corda.v5.cipher.suite.schemes.ECDSA_SECP256R1_CODE_NAME
import net.corda.v5.cipher.suite.schemes.ECDSA_SECP256R1_SHA256_TEMPLATE
import net.corda.v5.cipher.suite.schemes.RSA_CODE_NAME
import net.corda.v5.cipher.suite.schemes.RSA_SHA256_TEMPLATE
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.contains
import org.junit.jupiter.api.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import java.util.UUID
import kotlin.test.assertEquals

class CryptoServiceRefUtilsTests {
    @Test
    fun `Should return supported schemes`() {
        val schemes = arrayOf(
            ECDSA_SECP256R1_SHA256_TEMPLATE.makeScheme("BC"),
            RSA_SHA256_TEMPLATE.makeScheme("BC"),
        )
        val ref = CryptoServiceRef(
            tenantId = UUID.randomUUID().toString(),
            category = CryptoConsts.HsmCategories.LEDGER,
            signatureScheme = schemes[0],
            masterKeyAlias = UUID.randomUUID().toString(),
            aliasSecret = ByteArray(32),
            instance = mock {
                on { supportedSchemes() } doReturn schemes
            }
        )
        val result = ref.getSupportedSchemes()
        assertEquals(2, result.size)
        assertThat(result, contains(ECDSA_SECP256R1_CODE_NAME, RSA_CODE_NAME))
    }
}