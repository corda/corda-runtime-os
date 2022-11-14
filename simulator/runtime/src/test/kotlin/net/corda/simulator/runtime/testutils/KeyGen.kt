package net.corda.simulator.runtime.testutils

import net.corda.simulator.crypto.HsmCategory
import net.corda.simulator.runtime.signing.BaseSimKeyStore
import java.security.PublicKey
import java.util.UUID

fun generateKey() = generateKeys(1)[0]

fun generateKeys(numberOfKeys: Int) = generateKeys(
    aliases = (1.. numberOfKeys).map { UUID.randomUUID().toString() }.toTypedArray()
)

fun generateKeys(vararg aliases: String): List<PublicKey> {
    val keyStore = BaseSimKeyStore()
    return aliases.map { keyStore.generateKey(it, HsmCategory.LEDGER, "any-scheme") }
}

