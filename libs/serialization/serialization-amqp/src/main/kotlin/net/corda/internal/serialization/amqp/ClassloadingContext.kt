package net.corda.internal.serialization.amqp

interface ClassloadingContext {

    fun getClass(className: String, serialisedClassTag: String): Class<*>

    fun loadClassFromPublicBundles(className: String): Class<*>?

    fun loadClassFromMainBundles(className: String): Class<*>

    fun <T : Any> loadClassFromMainBundles(className: String, type: Class<T>): Class<out T>

    fun getEvolvableTag(klass: Class<*>): String
}
