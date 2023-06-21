package net.corda.interop.write.service

import net.corda.lifecycle.Lifecycle
import net.corda.data.interop.InteropAliasIdentity


interface InteropWriteService : Lifecycle {
    fun put(key: String, value: InteropAliasIdentity)
}