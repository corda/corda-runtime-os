@file:JvmName("KotlinCommon")

package net.corda.kotlin.reflect.impl

import net.corda.kotlin.reflect.types.KFunctionInternal
import net.corda.kotlin.reflect.types.KMutablePropertyAccessorInternal
import net.corda.kotlin.reflect.types.KMutablePropertyInternal
import net.corda.kotlin.reflect.types.KPropertyAccessorInternal
import net.corda.kotlin.reflect.types.KPropertyInternal
import net.corda.kotlin.reflect.types.MemberSignature
import net.corda.kotlin.reflect.types.isAbstract
import net.corda.kotlin.reflect.types.isForInterface
import net.corda.kotlin.reflect.types.isKotlin
import java.lang.reflect.Method
import java.util.Collections.unmodifiableCollection

val <T> Collection<T>.unmodifiable: Collection<T>
    get() = if (isEmpty()) {
        emptyList()
    } else {
        unmodifiableCollection(this)
    }

fun <T, R> MutableCollection<T>.extractAllBy(extractor: (T) -> R?): List<R> {
    val allExtracted = mutableListOf<R>()
    val iter = iterator()
    while (iter.hasNext()) {
        extractor(iter.next())?.let { extracted ->
            allExtracted.add(extracted)
            iter.remove()
        }
    }
    return allExtracted
}

fun <T : KPropertyInternal<*>> T.extractDeclaredAccessorsFrom(
    methods: MutableMap<MemberSignature, Method>
): T? {
    @Suppress("unchecked_cast")
    return if (this is KMutablePropertyInternal<*>) {
        // Refuse to recognise this var property unless it has both accessors.
        val jvmGetter = methods[getterSignature] ?: return null
        val jvmSetter = methods[setterSignature] ?: return null
        if (isAbstract(jvmGetter) || isAbstract(jvmSetter)) {
            return null
        }
        methods.remove(getterSignature)
        methods.remove(setterSignature)
        withJavaAccessors(jvmGetter, jvmSetter)
    } else {
        val jvmGetter = methods[getterSignature] ?: return null
        if (isAbstract(jvmGetter)) {
            return null
        }
        methods.remove(getterSignature)
        withJavaGetter(jvmGetter)
    } as? T
}

@Suppress("unchecked_cast", "ComplexMethod")
fun <T : KPropertyInternal<*>> T.populateMemberAccessorsFrom(
    methods: MutableMap<MemberSignature, Method>
): T? {
    // This could be a @JvmField property from a Kotlin base class.
    val fallback = javaField?.let(::withJavaField) as? T
    return if (this is KMutablePropertyInternal<*>) {
        val jvmGetter = methods[getterSignature] ?: javaGetter ?: return fallback
        val jvmSetter = methods[setterSignature] ?: javaSetter ?: return fallback
        methods.remove(getterSignature)
        methods.remove(setterSignature)
        withJavaAccessors(jvmGetter, jvmSetter)
    } else {
        val jvmGetter = methods[getterSignature] ?: javaGetter ?: return fallback
        methods.remove(getterSignature)
        withJavaGetter(jvmGetter)
    } as? T
}

@Suppress("ComplexMethod")
fun KPropertyInternal<*>.acceptIncompleteAccessors(accessors: MutableList<KFunctionInternal<*>>) {
    if (this is KMutablePropertyAccessorInternal<*>) {
        val isAbstractGetter = getter.isAbstract
        val isAbstractSetter = setter.isAbstract
        if (isAbstractGetter || isAbstractSetter) {
            if (!isAbstractGetter || !getter.javaMethod.isForInterface) {
                getter.toFunction()?.let(accessors::add)
            }
            if (!isAbstractSetter || !setter.javaMethod.isForInterface) {
                setter.toFunction()?.let(accessors::add)
            }
        }
    } else if (this is KPropertyAccessorInternal<*> &&
        (getter.isAbstract && !getter.javaMethod.isForInterface)
    ) {
        getter.toFunction()?.let(accessors::add)
    }
}

fun KPropertyInternal<*>.acceptJavaAccessors(accessors: MutableList<KFunctionInternal<*>>) {
    if (this is KMutablePropertyAccessorInternal<*> && !setter.javaMethod.isKotlin) {
        setter.toFunction()?.let(accessors::add)
    }
    if (this is KPropertyAccessorInternal<*> && !getter.javaMethod.isKotlin) {
        getter.toFunction()?.let(accessors::add)
    }
}

fun <T> KFunctionInternal<T>.extractMethodFrom(
    methods: MutableMap<MemberSignature, Method>
): KFunctionInternal<T>? {
    return signature?.let { sig ->
        methods.remove(sig)?.let(::withJavaMethod)
    }
}

fun <T> KFunctionInternal<T>.captureFor(clazz: Class<*>): KFunctionInternal<T>? {
    return takeIf { javaMethod?.declaringClass === clazz }
}
