@file:JvmName("Tools")
package net.corda.testing.driver.sandbox

inline fun <T> MutableCollection<T>.extractAllTo(
    destination: MutableCollection<T>,
    predicate: (T) -> Boolean
): MutableCollection<T> {
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
