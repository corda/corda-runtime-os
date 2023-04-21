package net.corda.kotlin.reflect.impl

import kotlinx.metadata.KmFunction
import kotlinx.metadata.KmProperty
import kotlinx.metadata.jvm.JvmFieldSignature
import net.corda.kotlin.reflect.types.KFunctionInternal
import net.corda.kotlin.reflect.types.KMutablePropertyInternal
import net.corda.kotlin.reflect.types.KPropertyInternal
import net.corda.kotlin.reflect.types.KotlinFunction
import net.corda.kotlin.reflect.types.KotlinProperty1
import net.corda.kotlin.reflect.types.KotlinProperty2
import net.corda.kotlin.reflect.types.MemberOverrideMap
import net.corda.kotlin.reflect.types.MemberSignature
import net.corda.kotlin.reflect.types.createKotlinProperty1
import net.corda.kotlin.reflect.types.createKotlinProperty2
import net.corda.kotlin.reflect.types.declaredMemberFields
import net.corda.kotlin.reflect.types.declaredMemberMethods
import net.corda.kotlin.reflect.types.getterSignature
import net.corda.kotlin.reflect.types.isExtension
import net.corda.kotlin.reflect.types.isInheritable
import net.corda.kotlin.reflect.types.nameOnlySignature
import net.corda.kotlin.reflect.types.signature
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.util.function.Function
import java.util.function.Supplier
import kotlin.LazyThreadSafetyMode.PUBLICATION
import kotlin.reflect.KCallable
import kotlin.reflect.KFunction
import kotlin.reflect.KProperty1
import kotlin.reflect.KProperty2

class KotlinMembers<T : Any>(
    clazz: Class<T>,
    kmProperties: List<KmProperty>,
    kmFunctions: List<KmFunction>,
    private val jvmSuperKotlinClasses: Supplier<List<KotlinClassImpl<*>>>,
    private val allSuperKotlinClasses: Supplier<List<KotlinClassImpl<*>>>
) {
    private val declaredMemberMethods: Map<MemberSignature, Method> by lazy(PUBLICATION, clazz::declaredMemberMethods)
    private val declaredMemberFields: Map<JvmFieldSignature, Field> by lazy(PUBLICATION, clazz::declaredMemberFields)

    private val declaredMemberPopulator: Populator
        get() = Populator(declaredMemberMethods::get, declaredMemberFields::get)

    val properties: Collection<KProperty1<T, *>> by lazy(PUBLICATION) {
        @Suppress("unchecked_cast")
        clazz.computeMembers(
            ::declaredProperties,
            KotlinClassImpl<*>::declaredMemberProperties,
            KProperty1<*, *>::nameOnlySignature
        ).map { prop ->
            (prop as? KPropertyInternal<*>)?.asPropertyFor(clazz) ?: prop
        } as Collection<KProperty1<T, *>>
    }

    val functions: Collection<KFunction<*>> by lazy(PUBLICATION) {
        clazz.computeMembers(
            ::declaredFunctions,
            KotlinClassImpl<*>::declaredMemberFunctions,
            KFunction<*>::signature,
        ).map { func ->
            (func as? KFunctionInternal<*>)?.asFunctionFor(clazz, isExtension = false) ?: func
        }
    }

    val extensionProperties: Collection<KProperty2<T, *, *>> by lazy(PUBLICATION) {
        @Suppress("unchecked_cast")
        clazz.computeMembers(
            ::declaredExtensionProperties,
            KotlinClassImpl<*>::declaredMemberExtensionProperties,
            KProperty2<*, *, *>::getterSignature
        ).map { prop ->
            (prop as? KPropertyInternal<*>)?.asPropertyFor(clazz) ?: prop
        } as Collection<KProperty2<T, *, *>>
    }

    val extensionFunctions: Collection<KFunction<*>> by lazy(PUBLICATION) {
        clazz.computeMembers(
            ::declaredExtensionFunctions,
            KotlinClassImpl<*>::declaredMemberExtensionFunctions,
            KFunction<*>::signature
        ).map { func ->
            (func as? KFunctionInternal<*>)?.asFunctionFor(clazz, isExtension = true) ?: func
        }
    }

    private fun <T : KCallable<*>> Class<*>.computeMembers(
        declaredFetcher: Supplier<List<T>>,
        superFetcher: Function<KotlinClassImpl<*>, Collection<T>>,
        keyMapper: Function<T, MemberSignature>
    ): Collection<T> {
        return if (isInterface) {
            // Fetch the "super interfaces" using Kotlin's allSuperClasses.
            declaredFetcher.get().withSuperInterfaceMembers(superFetcher, keyMapper)
        } else {
            // Fetch the "super classes" using Java reflection.
            declaredFetcher.get().withSuperMembers(superFetcher, keyMapper)
        }
    }

    private fun <T : KCallable<*>> Collection<T>.withSuperMembers(
        superFetcher: Function<KotlinClassImpl<*>, Collection<T>>,
        keyMapper: Function<T, MemberSignature>
    ): Collection<T> {
        val allMembers = jvmSuperKotlinClasses.get()
            .flatMap(superFetcher::apply)
            .filter(::isInheritable)
            .associateByTo(MemberOverrideMap(), keyMapper::apply)
        return associateByTo(allMembers, keyMapper::apply).values
    }

    private fun <T : KCallable<*>> Collection<T>.withSuperInterfaceMembers(
        superFetcher: Function<KotlinClassImpl<*>, Collection<T>>,
        keyMapper: Function<T, MemberSignature>
    ): Collection<T> {
        val allMembers = allSuperKotlinClasses.get()
            .flatMap(superFetcher::apply)
            .associateByTo(MemberOverrideMap(), keyMapper::apply)
        return associateByTo(allMembers, keyMapper::apply).values
    }

    val declaredProperties: List<KProperty1<T, *>> by lazy(PUBLICATION) {
        kmProperties
            .filterNot(::isExtension)
            .map<KmProperty, KotlinProperty1<T, Any?>> { prop -> clazz.createKotlinProperty1(prop) }
            .map(declaredMemberPopulator::forProperty)
    }

    val declaredFunctions: List<KFunction<*>> by lazy(PUBLICATION) {
        kmFunctions
            .filterNot(::isExtension)
            .map<KmFunction, KotlinFunction<Any?>> { func -> KotlinFunction(func, clazz) }
            .map(declaredMemberPopulator::forFunction)
    }

    val declaredExtensionProperties: List<KProperty2<T, *, *>> by lazy(PUBLICATION) {
        kmProperties
            .filter(::isExtension)
            .map<KmProperty, KotlinProperty2<T, Any?, Any?>> { prop -> clazz.createKotlinProperty2(prop) }
            .map(declaredMemberPopulator::forProperty)
    }

    val declaredExtensionFunctions: List<KFunction<*>> by lazy(PUBLICATION) {
        kmFunctions
            .filter(::isExtension)
            .map<KmFunction, KotlinFunction<Any?>> { func -> KotlinFunction(func, clazz) }
            .map(declaredMemberPopulator::forFunction)
    }

    private class Populator(
         private val methodProvider: Function<MemberSignature, Method?>,
         private val fieldProvider: Function<JvmFieldSignature, Field?>
    ) {
        fun forFunction(function: KFunctionInternal<*>): KFunctionInternal<*> {
            val method = function.signature?.let(methodProvider::apply)
            return method?.let(function::withJavaMethod) ?: function
        }

        fun <K : KPropertyInternal<*>> forProperty(property: K): K {
            val accessor = if (property is KMutablePropertyInternal<*>) {
                val setter = property.setterSignature?.let(methodProvider::apply)
                val getter = property.getterSignature?.let(methodProvider::apply)
                if (getter != null && setter != null) {
                    property.withJavaAccessors(getter, setter)
                } else {
                    property
                }
            } else {
                val getter = property.getterSignature?.let(methodProvider::apply)
                getter?.let(property::withJavaGetter) ?: property
            }
            val field = property.fieldSignature?.let(fieldProvider::apply)
            @Suppress("unchecked_cast")
            return (field?.let(accessor::withJavaField) ?: accessor) as K
        }
    }
}
