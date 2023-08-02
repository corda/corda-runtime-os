@file:JvmName("Tools")
package net.corda.testing.driver.sandbox

import org.osgi.framework.Bundle
import org.osgi.framework.wiring.BundleRevision

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

val Bundle.isFragment: Boolean
    get() = (adapt(BundleRevision::class.java).types and BundleRevision.TYPE_FRAGMENT) != 0
