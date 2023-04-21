package net.corda.kotlin.reflect.types

import java.lang.reflect.Field
import java.lang.reflect.Method
import kotlin.reflect.KMutableProperty

/**
 * Internal API for mutable Kotlin properties.
 */
interface KMutablePropertyInternal<V> : KPropertyInternal<V>, KMutableProperty<V> {
    val setterSignature: MemberSignature?
    val javaSetter: Method?

    fun withJavaAccessors(getter: Method, setter: Method): KMutablePropertyInternal<V>
    fun withJavaSetter(setter: Method): KMutablePropertyInternal<V>

    override fun withJavaGetter(getter: Method): KMutablePropertyInternal<V>
    override fun withJavaField(field: Field): KMutablePropertyInternal<V>
    override fun asPropertyFor(instanceClass: Class<*>): KMutablePropertyInternal<V>
}

interface KMutablePropertyAccessorInternal<V> : KMutablePropertyInternal<V>, KPropertyAccessorInternal<V> {
    override val setter: KSetterInternal<V>
}
