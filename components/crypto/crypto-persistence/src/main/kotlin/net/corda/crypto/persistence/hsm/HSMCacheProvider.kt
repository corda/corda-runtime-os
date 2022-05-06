package net.corda.crypto.persistence.hsm

import net.corda.lifecycle.Lifecycle

interface HSMCacheProvider : Lifecycle {
    fun getInstance(): HSMCache
}