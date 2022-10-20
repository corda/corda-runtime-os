package net.corda.ledger.common.testkit

import java.security.KeyPairGenerator
import java.security.PublicKey

private val kpg: KeyPairGenerator = KeyPairGenerator.getInstance("RSA").also {
    it.initialize(512)
}

val publicKeyExample: PublicKey = kpg.genKeyPair().public