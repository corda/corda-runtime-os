package net.corda.simulator.runtime.signing

import net.corda.simulator.crypto.HsmCategory
import net.corda.simulator.runtime.serialization.SimpleJsonMarshallingService
import net.corda.v5.crypto.SignatureSpec
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.`is`
import org.junit.jupiter.api.Test
import java.security.PublicKey

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

    @Test
    fun `should filter member keys from a set of keys`(){
        val keyStore = BaseSimKeyStore()
        val otherKeyStore = BaseSimKeyStore()
        val keySet = HashSet<PublicKey>()
        val myKeys = HashSet<PublicKey>()
        val service =  SimWithJsonSigningService(keyStore)
        repeat(5){
            keySet.add(otherKeyStore.generateKey("other-alias-$it", HsmCategory.LEDGER, "anyscheme"))
        }
        repeat(2){
            val publicKey = keyStore.generateKey("my-alias-$it", HsmCategory.LEDGER, "anyscheme")
            keySet.add(publicKey)
            myKeys.add(publicKey)
        }

        assertThat(service.findMySigningKeys(keySet).size, `is`(2))
        assertThat(service.findMySigningKeys(keySet).keys, `is`(myKeys))
    }

}