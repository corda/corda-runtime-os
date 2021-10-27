package net.corda.internal.serialization.osgi

object TypeResolver {

    fun resolve(className: String, classLoader: ClassLoader): Class<*>
        = Class.forName(className, false, classLoader)
}