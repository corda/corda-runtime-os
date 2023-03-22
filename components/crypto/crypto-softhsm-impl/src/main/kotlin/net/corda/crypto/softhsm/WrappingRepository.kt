package net.corda.crypto.softhsm

import net.corda.crypto.persistence.WrappingKeyInfo
import java.io.Closeable

interface WrappingRepository : Closeable {
    fun saveKey(alias: String, key: WrappingKeyInfo)
    fun findKey(alias: String): WrappingKeyInfo?
}