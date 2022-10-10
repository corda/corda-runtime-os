package net.corda.crypto.service.cipher.suite

import net.corda.crypto.core.aes.WrappingKey

class GeneratedWrappingKey(
    val alias: String,
    val key: WrappingKey
)