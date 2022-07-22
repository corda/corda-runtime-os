package net.corda.libs.configuration.datamodel.tests

import java.time.Instant

object Generator {
    fun randomHoldingIdentityShortHash() = randomHex(12)
    fun randomHex(len: Int) = List(len) { (('A'..'F') + ('0'..'9')).random() }.joinToString("")
    fun epochMillis() = Instant.now().toEpochMilli()
}
