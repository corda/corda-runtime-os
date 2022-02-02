package net.corda.introspiciere.core.addIdentity

import net.corda.introspiciere.core.addidentity.CreateKeysAndAddIdentityInteractor
import net.corda.introspiciere.core.addidentity.CryptoKeySender
import net.corda.p2p.test.KeyAlgorithm.ECDSA
import net.corda.p2p.test.KeyPairEntry
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

internal class CreateKeysAndAddIdentityInteractorTest {
    @Test
    fun first() {
        val sender = object : CryptoKeySender {
            override fun send(alias: String, keyPair: KeyPairEntry) {
                assertEquals("alice", alias, "Alias")
                assertEquals(ECDSA, keyPair.keyAlgo, "Key algorithm")
                assertEquals(170, keyPair.publicKey.remaining())
                assertEquals(104, keyPair.privateKey.remaining())
            }
        }

        CreateKeysAndAddIdentityInteractor(sender).execute(
            CreateKeysAndAddIdentityInteractor.Input("alice", ECDSA)
        )
    }
}