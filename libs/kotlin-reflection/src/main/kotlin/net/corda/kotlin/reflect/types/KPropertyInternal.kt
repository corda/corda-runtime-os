package net.corda.kotlin.reflect.types

import java.lang.reflect.Field
import java.lang.reflect.Method
import kotlin.reflect.KProperty
import kotlinx.metadata.jvm.JvmFieldSignature

/**
 * Internal API for Kotlin properties.
 */
interface KPropertyInternal<V> : KProperty<V>, KInternal {
    val getterSignature: MemberSignature?
    val javaGetter: Method?

    val fieldSignature: JvmFieldSignature?
    val javaField: Field?

    fun withJavaGetter(getter: Method): KPropertyInternal<V>
    fun withJavaField(field: Field): KPropertyInternal<V>
    fun asPropertyFor(instanceClass: Class<*>): KPropertyInternal<V>
}

interface KPropertyAccessorInternal<V> : KPropertyInternal<V> {
    override val getter: KGetterInternal<V>
}
