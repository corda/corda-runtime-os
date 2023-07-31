@file:JvmName("Tools")
package net.corda.testing.driver.sandbox

inline fun <T, C: MutableCollection<T>> MutableCollection<T>.extractAllTo(
    destination: C,
    predicate: (T) -> Boolean
): C {
    val iterator = iterator()
    while (iterator.hasNext()) {
        val item = iterator.next()
        if (predicate(item)) {
            destination += item
            iterator.remove()
        }
    }
    return destination
}
