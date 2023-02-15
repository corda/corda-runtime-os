package net.corda.crypto.softhsm.impl.infra

import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import net.corda.cache.caffeine.CacheFactoryImpl
import net.corda.crypto.core.aes.WrappingKey
import java.util.concurrent.TimeUnit

fun makeWrappingKeyCache(): Cache<String, WrappingKey> = CacheFactoryImpl().build(
    "test wrapping key cache", Caffeine.newBuilder()
        .expireAfterAccess(3600, TimeUnit.MINUTES)
        .maximumSize(100)
)