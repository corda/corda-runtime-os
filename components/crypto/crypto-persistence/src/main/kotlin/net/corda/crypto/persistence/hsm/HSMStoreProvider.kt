package net.corda.crypto.persistence.hsm

import net.corda.lifecycle.Lifecycle

interface HSMStoreProvider : Lifecycle {
    fun getInstance(): HSMStore
}