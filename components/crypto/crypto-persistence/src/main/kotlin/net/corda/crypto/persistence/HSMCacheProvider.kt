package net.corda.crypto.persistence

import net.corda.lifecycle.Lifecycle

interface HSMCacheProvider : Lifecycle {
    fun getInstance(): HSMCache
}