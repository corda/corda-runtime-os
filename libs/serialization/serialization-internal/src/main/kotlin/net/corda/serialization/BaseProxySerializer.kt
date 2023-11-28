package net.corda.serialization

/**
 * Optional base class for internal proxy serializers that
 * do not need to access the [SerializationContext].
 */
abstract class BaseProxySerializer<T : Any, P : Any> : InternalProxySerializer<T, P> {
    protected abstract fun toProxy(obj: T): P
    protected abstract fun fromProxy(proxy: P): T

    override fun toProxy(obj: T, context: SerializationContext): P = toProxy(obj)
    override fun fromProxy(proxy: P, context: SerializationContext): T = fromProxy(proxy)
}
