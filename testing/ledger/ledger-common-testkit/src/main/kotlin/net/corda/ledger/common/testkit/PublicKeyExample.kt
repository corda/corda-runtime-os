package net.corda.ledger.common.testkit

import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.PublicKey
import java.security.spec.ECGenParameterSpec

private val kpg = KeyPairGenerator.getInstance("EC")
    .apply { initialize(ECGenParameterSpec("secp256r1")) }

val keyPairExample: KeyPair = kpg.generateKeyPair()

val publicKeyExample: PublicKey = keyPairExample.public

val anotherPublicKeyExample: PublicKey = kpg
    .generateKeyPair().public

fun generatePublicKey(): PublicKey = KeyPairGenerator.getInstance("EC")
    .also { it.initialize(ECGenParameterSpec("secp256r1")) }.genKeyPair().public