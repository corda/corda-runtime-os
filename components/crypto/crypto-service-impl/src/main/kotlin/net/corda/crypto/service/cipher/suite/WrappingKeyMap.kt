package net.corda.crypto.service.cipher.suite

import net.corda.crypto.core.aes.WrappingKey

interface WrappingKeyMap {
    fun getWrappingKey(alias: String): WrappingKey
    fun getWrappingKey(): GeneratedWrappingKey
}