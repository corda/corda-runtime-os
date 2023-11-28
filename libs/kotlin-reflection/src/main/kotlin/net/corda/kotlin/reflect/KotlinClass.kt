@file:JvmName("KotlinReflection")

package net.corda.kotlin.reflect

import net.corda.kotlin.reflect.impl.createFrom
import net.corda.kotlin.reflect.types.KFunctionInternal
import net.corda.kotlin.reflect.types.KMutablePropertyInternal
import net.corda.kotlin.reflect.types.KPropertyInternal
import java.lang.reflect.Field
import java.lang.reflect.Method
import kotlin.reflect.KCallable
import kotlin.reflect.KClass
import kotlin.reflect.KFunction
import kotlin.reflect.KMutableProperty
import kotlin.reflect.KProperty
import kotlin.reflect.KProperty0
import kotlin.reflect.KProperty1
import kotlin.reflect.KProperty2
import kotlin.reflect.KType
import kotlin.reflect.KVisibility
import kotlin.reflect.jvm.javaField as reflectJavaField
import kotlin.reflect.jvm.javaGetter as reflectJavaGetter
import kotlin.reflect.jvm.javaMethod as reflectJavaMethod
import kotlin.reflect.jvm.javaSetter as reflectJavaSetter

interface KotlinClass<T : Any> : KClass<T> {
    override val visibility: KVisibility?
    override val simpleName: String?
    override val qualifiedName: String?
    override val objectInstance: T?
    override val constructors: Collection<KFunction<T>>
    val primaryConstructor: KFunction<T>?
    val superclasses: List<KotlinClass<*>>
    val allSuperKotlinClasses: Collection<KotlinClass<*>>
    override val supertypes: List<KType>

    override val isAbstract: Boolean
    override val isCompanion: Boolean
    override val isData: Boolean
    override val isFinal: Boolean
    override val isFun: Boolean
    override val isInner: Boolean
    override val isOpen: Boolean
    override val isSealed: Boolean

    override fun isInstance(value: Any?): Boolean

    val declaredMemberProperties: Collection<KProperty1<T, *>>
    val declaredMemberFunctions: Collection<KFunction<*>>
    val declaredMemberExtensionProperties: Collection<KProperty2<T, *, *>>
    val declaredMemberExtensionFunctions: Collection<KFunction<*>>
    val declaredMembers: Collection<KCallable<*>>

    val memberProperties: Collection<KProperty1<T, *>>
    val memberFunctions: Collection<KFunction<*>>
    val memberExtensionProperties: Collection<KProperty2<T, *, *>>
    val memberExtensionFunctions: Collection<KFunction<*>>

    val staticProperties: Collection<KProperty0<*>>
    val staticFunctions: Collection<KFunction<*>>

    val functions: Collection<KFunction<*>>

    override val members: Collection<KCallable<*>>

    fun findPropertyForGetter(getter: Method): KProperty<*>?
    fun findFunctionForMethod(method: Method): KFunction<*>?
}

/**
 * Returns a [KotlinClass] object for a given [Class].
 * This lets us retrieve "reflection-like" information
 * for Kotlin classes, and works around a bug in Kotlin.
 *
 * See [KT-47232](https://youtrack.jetbrains.com/issue/KT-47232)
 */
val <T : Any> Class<T>.kotlinClass: KotlinClass<T> get() {
    return createFrom(this, kotlin)
}

/**
 * Returns a [KotlinClass] object for a given [KClass].
 * This lets us retrieve "reflection-like" information
 * for Kotlin classes, and works around a bug in Kotlin.
 *
 * See [KT-47232](https://youtrack.jetbrains.com/issue/KT-47232)
 */
val <T : Any> KClass<T>.kotlinClass: KotlinClass<T> get() {
    return createFrom(java, this)
}

/**
 * Fetch the underlying Java [Field] for a property,
 * or `null` if the property has no backing field.
 * Falls back to actual Kotlin Reflection for "native"
 * Java/Kotlin classes.
 */
val KProperty<*>.kotlinJavaField: Field?
    get() {
        return if (this is KPropertyInternal<*>) {
            javaField
        } else {
            reflectJavaField
        }
    }

/**
 * Fetch the underlying Java [Method] for a property's getter,
 * or `null` if the property has no getter.
 * Falls back to actual Kotlin Reflection for "native"
 * Java/Kotlin classes.
 */
val KProperty<*>.kotlinJavaGetter: Method?
    get() {
        return if (this is KPropertyInternal<*>) {
            javaGetter
        } else {
            reflectJavaGetter
        }
    }

/**
 * Fetch the underlying Java [Method] for a property's setter,
 * or `null` if the property has no setter.
 * Falls back to actual Kotlin Reflection for "native"
 * Java/Kotlin classes.
 */
val KMutableProperty<*>.kotlinJavaSetter: Method?
    get() {
        return if (this is KMutablePropertyInternal<*>) {
            javaSetter
        } else {
            reflectJavaSetter
        }
    }

/**
 * Fetch the underlying Java [Method] for a function.
 * Falls back to actual Kotlin Reflection for "native"
 * Java/Kotlin classes.
 */
val KFunction<*>.kotlinJavaMethod: Method?
    get() {
        return if (this is KFunctionInternal<*>) {
            javaMethod
        } else {
            reflectJavaMethod
        }
    }
