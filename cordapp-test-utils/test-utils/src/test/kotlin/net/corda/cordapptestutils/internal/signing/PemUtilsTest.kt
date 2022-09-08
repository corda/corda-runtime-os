package net.corda.cordapptestutils.internal.signing

import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.`is`
import org.junit.jupiter.api.Test
import java.security.KeyPairGenerator

class PemUtilsTest {

    @Test
    fun `should be able to pem encode and decode a key`() {
        val publicKey = KeyPairGenerator.getInstance("EC").generateKeyPair().public

        val encoded = pemEncode(publicKey)
        val decoded = pemDecode(encoded)

        println(encoded)

        assertThat(pemEncode(decoded), `is`(encoded))
    }
}