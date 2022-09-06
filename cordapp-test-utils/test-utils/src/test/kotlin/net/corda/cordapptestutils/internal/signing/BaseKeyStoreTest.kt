package net.corda.cordapptestutils.internal.signing

import net.corda.cordapptestutils.crypto.HsmCategory
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.`is`
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.security.KeyPairGenerator

class BaseKeyStoreTest {

    @Test
    fun `should generate and store a public key`() {
        // Given a key store
        val keyStore = BaseKeyStore()

        // When I generate a key
        val publicKey = keyStore.generateKey("my-alias", HsmCategory.LEDGER, "Any scheme will do")

        // Then I should be able to get the parameters with which it was generated
        assertThat(keyStore.getParameters(publicKey),
            `is`(KeyParameters("my-alias", HsmCategory.LEDGER, "Any scheme will do"))
        )
    }

    @Test
    fun `should return null if public key was not created by key store`() {
        val keyStore = BaseKeyStore()
        val publicKey = KeyPairGenerator.getInstance("RSA").generateKeyPair().public

        assertNull(keyStore.getParameters(publicKey))
    }
}