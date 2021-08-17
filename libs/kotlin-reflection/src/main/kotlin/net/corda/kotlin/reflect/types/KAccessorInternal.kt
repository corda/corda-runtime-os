package net.corda.kotlin.reflect.types

import java.lang.reflect.Method
import kotlin.reflect.KFunction
import kotlin.reflect.KMutableProperty
import kotlin.reflect.KMutableProperty1
import kotlin.reflect.KMutableProperty2
import kotlin.reflect.KProperty
import kotlin.reflect.KProperty1
import kotlin.reflect.KProperty2

/**
 * Internal API for Kotlin getter and setter functions.
 */
interface KAccessorInternal<V> : KFunction<V>, KInternal {
    val javaMethod: Method?

    fun toFunction(): KFunctionInternal<V>?
}

interface KGetterInternal<V> : KProperty.Getter<V>, KAccessorInternal<V>
interface KGetter1Internal<T, V> : KProperty1.Getter<T, V>, KGetterInternal<V>
interface KGetter2Internal<D, E, V> : KProperty2.Getter<D, E, V>, KGetterInternal<V>

interface KSetterInternal<V> : KMutableProperty.Setter<V>, KAccessorInternal<Unit>
interface KSetter1Internal<T, V> : KMutableProperty1.Setter<T, V>, KSetterInternal<V>
interface KSetter2Internal<D, E, V> : KMutableProperty2.Setter<D, E, V>, KSetterInternal<V>
