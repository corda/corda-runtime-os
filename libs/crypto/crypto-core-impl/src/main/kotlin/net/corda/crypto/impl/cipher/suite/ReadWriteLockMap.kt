package net.corda.crypto.impl.cipher.suite

import java.util.concurrent.locks.ReadWriteLock
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.withLock

class ReadWriteLockMap<K, V> {
    private val lock: ReadWriteLock = ReentrantReadWriteLock()
    private val map = mutableMapOf<K, V>()

    fun <T> withWriteLock(action: (MutableMap<K, V>) -> T): T = lock.writeLock().withLock {
        action(map)
    }

    fun getWithReadLock(key: K): V? = lock.readLock().withLock {
        map[key]
    }

    fun getValueWithReadLock(key: K): V = lock.readLock().withLock {
        map.getValue(key)
    }

    fun getAllValuesWithReadLock(): List<V> = lock.readLock().withLock {
        map.values.toList()
    }
}