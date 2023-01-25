package net.corda.ledger.common.testkit

import java.security.KeyPairGenerator
import java.security.PublicKey
import java.security.spec.ECGenParameterSpec


val publicKeyExample: PublicKey = KeyPairGenerator.getInstance("EC")
    .apply { initialize(ECGenParameterSpec("secp256r1")) }
    .generateKeyPair().public