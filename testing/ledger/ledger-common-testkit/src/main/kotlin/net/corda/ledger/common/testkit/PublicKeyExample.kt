package net.corda.ledger.common.testkit

import java.security.KeyPairGenerator
import java.security.PublicKey
import java.security.spec.ECGenParameterSpec

private val kpg = KeyPairGenerator.getInstance("EC")
    .apply { initialize(ECGenParameterSpec("secp256r1")) }

val publicKeyExample: PublicKey = kpg
    .generateKeyPair().public

val anotherPublicKeyExample: PublicKey = kpg
    .generateKeyPair().public