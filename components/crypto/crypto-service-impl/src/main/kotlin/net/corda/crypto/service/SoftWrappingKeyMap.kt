package net.corda.crypto.service

import net.corda.crypto.core.aes.WrappingKey

interface SoftWrappingKeyMap {
    fun getWrappingKey(alias: String): WrappingKey
    fun putWrappingKey(alias: String, wrappingKey: WrappingKey)
    fun exists(alias: String): Boolean
}