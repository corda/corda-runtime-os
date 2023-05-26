package net.corda.flow.application.crypto

import net.corda.v5.base.annotations.Suspendable
import java.security.PublicKey

interface MySigningKeysCache {

    fun get(keys: Set<PublicKey>): Map<PublicKey, PublicKey?>
    fun putAll(keys: Map<out PublicKey, PublicKey?>)
}