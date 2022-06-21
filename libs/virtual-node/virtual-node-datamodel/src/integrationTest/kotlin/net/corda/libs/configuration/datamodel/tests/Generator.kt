package net.corda.libs.configuration.datamodel.tests

import java.time.Instant

object Generator {
    fun randomHex(len: Int = 12) = List(len) { (('A'..'F') + ('0'..'9')).random() }.joinToString("")
    fun epochMillis() = Instant.now().toEpochMilli()
}
