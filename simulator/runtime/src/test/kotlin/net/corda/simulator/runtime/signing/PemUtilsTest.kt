package net.corda.simulator.runtime.signing

import net.corda.simulator.runtime.testutils.generateKey
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.`is`
import org.junit.jupiter.api.Test

class PemUtilsTest {

    @Test
    fun `should be able to pem encode and decode a key`() {

        val publicKey = generateKey()

        val encoded = pemEncode(publicKey)
        val decoded = pemDecode(encoded)

        assertThat(decoded, `is`(publicKey))
    }
}