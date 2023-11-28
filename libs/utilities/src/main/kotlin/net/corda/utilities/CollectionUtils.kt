package net.corda.utilities

import java.util.Collections
import java.util.stream.Stream

/** Returns the index of the given item or throws [IllegalArgumentException] if not found. */
fun <T> List<T>.indexOfOrThrow(item: T): Int {
    val i = indexOf(item)
    require(i != -1) { "No such element" }
    return i
}

inline fun <T, R : Any> Stream<T>.mapNotNull(crossinline transform: (T) -> R?): Stream<R> {
    return this.map { transform(it) }.filter { it != null }.map { it }
}

fun <K, V> Iterable<Pair<K, V>>.toMultiMap(): Map<K, List<V>> = this.groupBy({ it.first }) { it.second }

/** @see Collections.synchronizedMap */
fun <K, V> MutableMap<K, V>.toSynchronised(): MutableMap<K, V> = Collections.synchronizedMap(this)

/** @see Collections.synchronizedSet */
fun <E> MutableSet<E>.toSynchronised(): MutableSet<E> = Collections.synchronizedSet(this)

/**
 * Returns a [List] implementation that applies the expensive [transform] function only when an element is accessed and then caches the
 * calculated values. Size is very cheap as it doesn't call [transform].
 */
fun <T, U> List<T>.lazyMapped(transform: (T, Int) -> U): List<U> = LazyMappedList(this, transform)
