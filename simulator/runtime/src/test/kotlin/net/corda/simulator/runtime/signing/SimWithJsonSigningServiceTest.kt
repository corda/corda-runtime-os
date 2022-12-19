package net.corda.simulator.runtime.signing

import net.corda.simulator.crypto.HsmCategory
import net.corda.simulator.runtime.serialization.SimpleJsonMarshallingService
import net.corda.v5.crypto.SignatureSpec
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.`is`
import org.junit.jupiter.api.Test

class SimWithJsonSigningServiceTest {

    @Test
    fun `should simulate signing by wrapping data in a json wrapper`() {

        // Given a signing service and some bytes
        val jsonMarshallingService = SimpleJsonMarshallingService()
        val keyStore = BaseSimKeyStore()
        val service =  SimWithJsonSigningService(keyStore)

        // When we add a key to the keystore and sign using the service
        val publicKey = keyStore.generateKey("my-alias", HsmCategory.LEDGER, "anyscheme")
        val signed = service.sign("Hello!".toByteArray(), publicKey, SignatureSpec.ECDSA_SHA256)

        // Then we should be able to see what we signed them with
        val result = jsonMarshallingService.parse(signed.bytes.decodeToString(), SimJsonSignedWrapper::class.java)
        val expected = SimJsonSignedWrapper(
            "Hello!".toByteArray(),
            pemEncode(publicKey),
            SignatureSpec.ECDSA_SHA256.signatureName,
            KeyParameters("my-alias", HsmCategory.LEDGER, "anyscheme"),
        )
        assertThat(result, `is`(expected))
    }


}