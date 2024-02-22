package net.corda.ledger.common.testkit

import net.corda.crypto.impl.CompositeKeyProviderImpl
import net.corda.v5.crypto.CompositeKeyNodeAndWeight
import java.security.PublicKey

fun generateCompositeKey(publicKeys: Pair<PublicKey, PublicKey>): PublicKey {
    return CompositeKeyProviderImpl().create(
        listOf(
            CompositeKeyNodeAndWeight(publicKeys.first, 2),
            CompositeKeyNodeAndWeight(publicKeys.second, 1)
        ), threshold = 2
    )
}