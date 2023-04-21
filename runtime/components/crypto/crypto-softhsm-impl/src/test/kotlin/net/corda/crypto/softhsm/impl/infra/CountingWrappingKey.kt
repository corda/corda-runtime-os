package net.corda.crypto.softhsm.impl.infra

import net.corda.crypto.core.aes.WrappingKey
import java.security.PrivateKey
import java.util.concurrent.atomic.AtomicInteger

class CountingWrappingKey(
    val inner: WrappingKey,
    val wrapCount: AtomicInteger,
    val unwrapCount: AtomicInteger
) : WrappingKey by inner {
    override fun unwrap(other: ByteArray): PrivateKey {
        unwrapCount.incrementAndGet()
        return inner.unwrap(other)
    }

    override fun unwrapWrappingKey(other: ByteArray): WrappingKey {
        return CountingWrappingKey(inner.unwrapWrappingKey(other), wrapCount, unwrapCount)
    }

    override fun wrap(other: WrappingKey): ByteArray {
        wrapCount.incrementAndGet()
        return inner.wrap(other)
    }

    override fun wrap(other: PrivateKey): ByteArray {
        wrapCount.incrementAndGet()
        return inner.wrap(other)
    }
}