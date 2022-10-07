package net.corda.v5.cipher.suite

import java.security.SecureRandom

interface SecureRandomProvider {
    val secureRandom: SecureRandom
}