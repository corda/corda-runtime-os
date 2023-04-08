package net.corda.utilities.classload

import org.osgi.framework.Bundle
import org.osgi.framework.wiring.BundleRevision
import org.osgi.framework.wiring.BundleRevision.TYPE_FRAGMENT
import java.net.URL
import java.util.Collections.emptyEnumeration
import java.util.Enumeration

/**
 * Allows combining multiple OSGi bundles under the same classloader. This might be useful in places where
 * `contextClassloader` is used and no single bundle can satisfy all the required dependencies.
 *
 * Inspired by `org.hibernate.osgi.OsgiClassLoader`, but is simpler and immutable.
 */
class OsgiClassLoader(bundles: List<Bundle>) : ClassLoader(null) {

    private companion object {
        init {
            registerAsParallelCapable()
        }

        private fun isFragment(bundle: Bundle): Boolean {
            return (bundle.adapt(BundleRevision::class.java).types and TYPE_FRAGMENT) != 0
        }
    }

    // Remove all fragment bundles from consideration.
    private val bundles = bundles.filterNot(::isFragment)

    override fun findClass(name: String?): Class<*> {
        val suppressed = mutableListOf<Throwable>()
        bundles.forEach { bundle ->
            try {
                return bundle.loadClass(name)
            } catch (e: Exception) {
                // ClassNotFoundException, IllegalStateException
                suppressed.add(e)
            }
        }
        throw ClassNotFoundException("Could not load requested class : $name").also { ex ->
            suppressed.forEach(ex::addSuppressed)
        }
    }

    override fun findResource(name: String?): URL? {
        bundles.forEach { bundle ->
            bundle.getResource(name)?.let { return it }
        }

        return null
    }

    override fun findResources(name: String?): Enumeration<URL> {
        val enumerations = bundles.mapNotNull { bundle ->
            bundle.getResources(name)
        }
        return CompoundEnumeration(enumerations)
    }

    private class CompoundEnumeration<E>(enumerations: Iterable<Enumeration<E>>) : Enumeration<E> {
        private val iterator = enumerations.iterator()
        private var current = if (iterator.hasNext()) {
            iterator.next()
        } else {
            emptyEnumeration()
        }

        init {
            nextValidPosition()
        }

        override fun hasMoreElements(): Boolean {
            return current.hasMoreElements()
        }

        override fun nextElement(): E {
            return current.nextElement().also {
                nextValidPosition()
            }
        }

        private fun nextValidPosition() {
            while (!current.hasMoreElements() && iterator.hasNext()) {
                current = iterator.next()
            }
        }
    }
}
