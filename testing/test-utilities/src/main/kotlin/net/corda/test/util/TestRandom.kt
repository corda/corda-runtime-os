package net.corda.test.util

import net.corda.v5.crypto.SecureHash

object TestRandom {
    fun hex(len: Int) = List(len) { (('A'..'F') + ('0'..'9')).random() }.joinToString("")

    fun holdingIdentityShortHash() = hex(12)

    fun secureHash(): SecureHash = SecureHash.parse("foo:${holdingIdentityShortHash()}")
}