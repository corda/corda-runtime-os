package net.corda.sandboxgroupcontext.service.impl

import net.corda.sandbox.SandboxGroup
import net.corda.sandboxgroupcontext.MutableSandboxGroupContext
import net.corda.sandboxgroupcontext.SandboxGroupContext
import net.corda.sandboxgroupcontext.VirtualNodeContext
import java.util.concurrent.ConcurrentHashMap
import kotlin.reflect.KClass
import kotlin.reflect.cast

/**
 * Implementation of [MutableSandboxGroupContext] and [SandboxGroupContext]
 *
 * Holds the "context" of a virtual node.
 *
 * The "core" information is in [VirtualNodeContext], and additional information can be found via the [get] method.
 */
class SandboxGroupContextImpl(
    override val virtualNodeContext: VirtualNodeContext,
    override val sandboxGroup: SandboxGroup
) :  MutableSandboxGroupContext, SandboxGroupContext {

    private data class TypedValue<T : Any>(val valueType: KClass<T>, val value: Any)

    private val objectByKey = ConcurrentHashMap<String, TypedValue<*>>()

    override fun <T : Any> put(key: String, valueType: KClass<T>, value: T) {
        val typedValue = TypedValue(valueType, value)
        if (objectByKey.containsKey(key)) {
            throw IllegalArgumentException("Attempt to overwrite existing object in cache with key:  $key")
        }
        objectByKey[key] = typedValue
    }

    override fun <T : Any> get(key: String, valueType: KClass<T>): T? {
        if (!objectByKey.containsKey(key)) {
            return null
        }
        val typedValue = objectByKey[key]!!
        return valueType.cast(typedValue.value)
    }
}
