@file:JvmName("KotlinClassUtils")
package net.corda.kotlin.reflect.impl

import kotlinx.metadata.KmClass
import kotlinx.metadata.KmPackage
import kotlinx.metadata.jvm.KotlinClassMetadata
import net.corda.kotlin.reflect.KotlinClass
import net.corda.kotlin.reflect.types.KFunctionInternal
import net.corda.kotlin.reflect.types.KPropertyInternal
import net.corda.kotlin.reflect.types.MemberSignature
import net.corda.kotlin.reflect.types.jvmSuperClasses
import net.corda.kotlin.reflect.types.toSignature
import java.lang.reflect.Method
import java.util.Collections.unmodifiableMap
import kotlin.LazyThreadSafetyMode.PUBLICATION
import kotlin.reflect.KCallable
import kotlin.reflect.KClass
import kotlin.reflect.KFunction
import kotlin.reflect.KProperty
import kotlin.reflect.KProperty0
import kotlin.reflect.KProperty1
import kotlin.reflect.KProperty2
import kotlin.reflect.KType
import kotlin.reflect.KTypeParameter
import kotlin.reflect.KVisibility
import kotlin.reflect.full.allSuperclasses
import kotlin.reflect.full.declaredMemberExtensionFunctions
import kotlin.reflect.full.declaredMemberExtensionProperties
import kotlin.reflect.full.declaredMemberFunctions
import kotlin.reflect.full.declaredMemberProperties
import kotlin.reflect.full.declaredMembers
import kotlin.reflect.full.functions
import kotlin.reflect.full.memberExtensionFunctions
import kotlin.reflect.full.memberExtensionProperties
import kotlin.reflect.full.memberFunctions
import kotlin.reflect.full.memberProperties
import kotlin.reflect.full.primaryConstructor
import kotlin.reflect.full.staticFunctions
import kotlin.reflect.full.staticProperties
import kotlin.reflect.full.superclasses

sealed class KotlinClassImpl<T : Any> constructor(
    @JvmField protected val clazz: Class<T>,
    @JvmField protected val klazz: KClass<T>,
    @JvmField protected val kClassPool: KClassPool
) : KotlinClass<T>, Comparable<KotlinClassImpl<T>> {
    override val visibility: KVisibility? get() = klazz.visibility
    override val simpleName: String? get() = klazz.simpleName
    override val qualifiedName: String? get() = klazz.qualifiedName
    override val objectInstance: T? get() = klazz.objectInstance
    override val constructors: Collection<KFunction<T>> get() = klazz.constructors
    override val primaryConstructor: KFunction<T>? get() = klazz.primaryConstructor
    override val supertypes: List<KType> get() = klazz.supertypes
    override val annotations: List<Annotation> get() = klazz.annotations
    override val typeParameters: List<KTypeParameter> get() = klazz.typeParameters
    override val sealedSubclasses: List<KClass<out T>> get() = klazz.sealedSubclasses
    override val nestedClasses: Collection<KotlinClass<*>> get() = TODO("Not yet implemented")

    override val isAbstract: Boolean get() = klazz.isAbstract
    override val isCompanion: Boolean get() = klazz.isCompanion
    override val isData: Boolean get() = klazz.isData
    override val isFinal: Boolean get() = klazz.isFinal
    override val isFun: Boolean get() = klazz.isFun
    override val isInner: Boolean get() = klazz.isInner
    override val isOpen: Boolean get() = klazz.isOpen
    override val isSealed: Boolean get() = klazz.isSealed
    override val isValue: Boolean get() = klazz.isValue

    override fun isInstance(value: Any?): Boolean = klazz.isInstance(value)

    final override val superclasses: List<KotlinClass<*>>
        get() = klazz.superclasses.mapNotNull(kClassPool::get)
    final override val allSuperKotlinClasses: List<KotlinClassImpl<*>>
        @Suppress("unchecked_cast")
        get() = klazz.allSuperclasses
                    .mapNotNullTo(mutableListOf(), kClassPool::get)
                    .also { (it as MutableList<KotlinClassImpl<Any>>).sort() }

    final override fun compareTo(other: KotlinClassImpl<T>): Int {
        return when {
            this.clazz === other.clazz -> 0
            else -> {
                val otherFromThis = other.clazz.isAssignableFrom(clazz)
                val thisFromOther = clazz.isAssignableFrom(other.clazz)
                when {
                    thisFromOther != otherFromThis ->
                        if (thisFromOther) {
                            -1
                        } else {
                            1
                        }
                    else ->
                        clazz.name.compareTo(other.clazz.name)
                }
            }
        }
    }

    protected val jvmSuperKotlinClasses: List<KotlinClassImpl<*>>
        get() = clazz.jvmSuperClasses.map { sup ->
            kClassPool[sup.kotlin] ?: throw IllegalStateException("Missing superclass $sup")
        }

    protected abstract val declaredMemberKProps: Collection<KProperty1<T, *>>
    protected abstract val declaredMemberKFuncs: Collection<KFunction<*>>
    protected abstract val declaredMemberExtensionKProps: Collection<KProperty2<T, *, *>>
    protected abstract val declaredMemberExtensionKFuncs: Collection<KFunction<*>>
    protected open val declaredStaticKProps: Collection<KProperty0<*>>
        get() = emptyList()
    protected open val declaredStaticKFuncs: Collection<KFunction<*>>
        get() = emptyList()
    protected open val allDeclaredMembers: Collection<KCallable<*>>
        get() = (
            declaredMemberKProps
                + declaredMemberExtensionKProps
                + declaredStaticKProps
                + declaredMemberKFuncs
                + declaredMemberExtensionKFuncs
                + declaredStaticKFuncs
        )

    protected abstract val memberKProps: Collection<KProperty1<T, *>>
    protected abstract val memberKFuncs: Collection<KFunction<*>>
    protected abstract val memberExtensionKProps: Collection<KProperty2<T, *, *>>
    protected abstract val memberExtensionKFuncs: Collection<KFunction<*>>
    protected open val allMembers: Collection<KCallable<*>>
        get() = (
            memberKProps
                + memberExtensionKProps
                + allStaticProperties
                + memberKFuncs
                + memberExtensionKFuncs
                + allStaticFunctions
        )

    protected open val allStaticProperties: Collection<KProperty0<*>>
        get() = emptyList()
    protected open val allStaticFunctions: Collection<KFunction<*>>
        get() = emptyList()

    protected open val allFunctions: Collection<KFunction<*>>
        get() = (
            memberKFuncs
                + memberExtensionKFuncs
                + allStaticFunctions
        )

    override val declaredMemberProperties: Collection<KProperty1<T, *>>
        get() = declaredMemberKProps.unmodifiable
    override val declaredMemberFunctions: Collection<KFunction<*>>
        get() = declaredMemberKFuncs.unmodifiable
    override val declaredMemberExtensionProperties: Collection<KProperty2<T, *, *>>
        get() = declaredMemberExtensionKProps.unmodifiable
    override val declaredMemberExtensionFunctions: Collection<KFunction<*>>
        get() = declaredMemberExtensionKFuncs.unmodifiable
    override val declaredMembers: Collection<KCallable<*>>
        get() = allDeclaredMembers.unmodifiable

    override val memberProperties: Collection<KProperty1<T, *>>
        get() = memberKProps.unmodifiable
    override val memberFunctions: Collection<KFunction<*>>
        get() = memberKFuncs.unmodifiable
    override val memberExtensionProperties: Collection<KProperty2<T, *, *>>
        get() = memberExtensionKProps.unmodifiable
    override val memberExtensionFunctions: Collection<KFunction<*>>
        get() = memberExtensionKFuncs.unmodifiable
    override val members: Collection<KCallable<*>>
        get() = allMembers.unmodifiable

    override val staticProperties: Collection<KProperty0<*>>
        get() = allStaticProperties.unmodifiable
    override val staticFunctions: Collection<KFunction<*>>
        get() = allStaticFunctions.unmodifiable

    override val functions: Collection<KFunction<*>>
        get() = allFunctions.unmodifiable

    override fun findPropertyForGetter(getter: Method): KProperty<*>? {
        return (getter to getter.toSignature()).findPropertyByGetterFrom(declaredMemberProperties)
    }

    override fun findFunctionForMethod(method: Method): KFunction<*>? {
        return (method to method.toSignature()).findFunctionFrom(declaredMemberFunctions)
    }

    override fun equals(other: Any?): Boolean {
        return when {
            this === other -> true
            other !is KotlinClassImpl<*> -> false
            else -> klazz == other.klazz
        }
    }

    override fun hashCode(): Int {
        return klazz.hashCode()
    }

    override fun toString(): String {
        return klazz.toString()
    }

    class KmClassType<T : Any> internal constructor(
        clazz: Class<T>,
        klazz: KClass<T>,
        classPool: KClassPool,
        kmClass: KmClass
    ) : KotlinClassImpl<T>(clazz, klazz, classPool) {
        private val kMembers = KotlinMembers(
            clazz,
            kmClass.properties,
            kmClass.functions,
            ::jvmSuperKotlinClasses,
            ::allSuperKotlinClasses
        )

        override val declaredMemberKProps: Collection<KProperty1<T, *>>
            get() = kMembers.declaredProperties
        override val declaredMemberExtensionKProps: Collection<KProperty2<T, *, *>>
            get() = kMembers.declaredExtensionProperties
        override val declaredMemberKFuncs: Collection<KFunction<*>>
            get() = kMembers.declaredFunctions
        override val declaredMemberExtensionKFuncs: Collection<KFunction<*>>
            get() = kMembers.declaredExtensionFunctions

        override val memberKProps: Collection<KProperty1<T, *>>
            get() = kMembers.properties
        override val memberExtensionKProps: Collection<KProperty2<T, *, *>>
            get() = kMembers.extensionProperties
        override val memberKFuncs: Collection<KFunction<*>>
            get() = kMembers.functions
        override val memberExtensionKFuncs: Collection<KFunction<*>>
            get() = kMembers.extensionFunctions
    }

    class KmPackageType<T : Any> internal constructor(
        clazz: Class<T>,
        klazz: KClass<T>,
        kClassPool: KClassPool,
        kmPackage: KmPackage
    ) : KotlinClassImpl<T>(clazz, klazz, kClassPool) {
        private val kMembers = KotlinMembers(
            clazz,
            kmPackage.properties,
            kmPackage.functions,
            ::jvmSuperKotlinClasses,
            ::allSuperKotlinClasses
        )

        override val declaredMemberKProps: Collection<KProperty1<T, *>>
            get() = kMembers.declaredProperties
        override val declaredMemberExtensionKProps: Collection<KProperty2<T, *, *>>
            get() = kMembers.declaredExtensionProperties
        override val declaredMemberKFuncs: Collection<KFunction<*>>
            get() = kMembers.declaredFunctions
        override val declaredMemberExtensionKFuncs: Collection<KFunction<*>>
            get() = kMembers.declaredExtensionFunctions

        override val memberKProps: Collection<KProperty1<T, *>>
            get() = kMembers.properties
        override val memberExtensionKProps: Collection<KProperty2<T, *, *>>
            get() = kMembers.extensionProperties
        override val memberKFuncs: Collection<KFunction<*>>
            get() = kMembers.functions
        override val memberExtensionKFuncs: Collection<KFunction<*>>
            get() = kMembers.extensionFunctions
    }

    class KmJavaType<T : Any> internal constructor(
        clazz: Class<T>,
        klazz: KClass<T>,
        kClassPool: KClassPool
    ): KotlinClassImpl<T>(clazz, klazz, kClassPool) {
        private val jMembers by lazy(PUBLICATION) {
            JavaMembers(clazz, allSuperKotlinClasses)
        }

        override val declaredMemberKProps: Collection<KProperty1<T, *>>
            get() = jMembers.declaredProperties
        override val declaredMemberExtensionKProps: Collection<KProperty2<T, *, *>>
            // I don't understand why Java classes cannot implement
            // an extension property from a Kotlin interface?!
            get() = emptyList()
        override val declaredMemberKFuncs: Collection<KFunction<*>>
            get() = jMembers.declaredFunctions
        override val declaredMemberExtensionKFuncs: Collection<KFunction<*>>
            // I don't understand why Java classes cannot implement
            // an extension function from a Kotlin interface?!
            get() = emptyList()
        override val declaredStaticKFuncs: Collection<KFunction<*>>
            get() = jMembers.declaredStaticFunctions
        override val declaredStaticKProps: Collection<KProperty0<*>>
            get() = jMembers.declaredStaticProperties

        override val memberKFuncs: Collection<KFunction<*>>
            get() = jMembers.functions
        override val memberExtensionKFuncs: Collection<KFunction<*>>
            get() = jMembers.extensionFunctions
        override val memberKProps: Collection<KProperty1<T, *>>
            get() = jMembers.properties
        override val memberExtensionKProps: Collection<KProperty2<T, *, *>>
            get() = jMembers.extensionProperties

        override val allStaticFunctions: Collection<KFunction<*>>
            get() = jMembers.staticFunctions
        override val allStaticProperties: Collection<KProperty0<*>>
            get() = jMembers.staticProperties
    }

    class KmNativeType<T : Any> internal constructor(
        clazz: Class<T>,
        klazz: KClass<T>,
        kClassPool: KClassPool
    ): KotlinClassImpl<T>(clazz, klazz, kClassPool) {
        override val declaredMemberKProps: Collection<KProperty1<T, *>>
            get() = klazz.declaredMemberProperties
        override val declaredMemberKFuncs: Collection<KFunction<*>>
            get() = klazz.declaredMemberFunctions
        override val declaredMemberExtensionKProps: Collection<KProperty2<T, *, *>>
            get() = klazz.declaredMemberExtensionProperties
        override val declaredMemberExtensionKFuncs: Collection<KFunction<*>>
            get() = klazz.declaredMemberExtensionFunctions
        override val allDeclaredMembers: Collection<KCallable<*>>
            get() = klazz.declaredMembers

        override val memberKProps: Collection<KProperty1<T, *>>
            get() = klazz.memberProperties
        override val memberKFuncs: Collection<KFunction<*>>
            get() = klazz.memberFunctions
        override val memberExtensionKProps: Collection<KProperty2<T, *, *>>
            get() = klazz.memberExtensionProperties
        override val memberExtensionKFuncs: Collection<KFunction<*>>
            get() = klazz.memberExtensionFunctions
        override val allMembers: Collection<KCallable<*>>
            get() = klazz.members

        override val allStaticProperties: Collection<KProperty0<*>>
            get() = klazz.staticProperties
        override val allStaticFunctions: Collection<KFunction<*>>
            get() = klazz.staticFunctions

        override val allFunctions: Collection<KFunction<*>>
            get() = klazz.functions
    }
}

private fun <V> Pair<Method, MemberSignature>.findPropertyByGetterFrom(properties: Iterable<KProperty<V>>): KProperty<V>? {
    for (property in properties) {
        if (property is KPropertyInternal && property.getterSignature == second) {
            return property.withJavaGetter(first)
        }
    }
    return null
}

private fun <T> Pair<Method, MemberSignature>.findFunctionFrom(functions: Iterable<KFunction<T>>): KFunction<T>? {
    for (function in functions) {
        if (function is KFunctionInternal && function.signature == second) {
            return function.withJavaMethod(first)
        }
    }
    return null
}

private const val KOTLIN_PREFIX = "kotlin."
private const val JAVA_PREFIX = "java."

typealias KClassPool = Map<KClass<*>, KotlinClassImpl<*>>

private val ANY = forNativeType(Any::class.java, Any::class, emptyMap())

val Class<*>.isNativeType: Boolean
    get() = name.startsWith(KOTLIN_PREFIX) || name.startsWith(JAVA_PREFIX)

private fun <T : Any> Metadata.forKotlinType(
    clazz: Class<T>,
    klazz: KClass<T>,
    pool: KClassPool
): KotlinClassImpl<T> {
    return when (val km = KotlinClassMetadata.read(this)) {
        is KotlinClassMetadata.Class -> KotlinClassImpl.KmClassType(clazz, klazz, pool, km.toKmClass())
        is KotlinClassMetadata.FileFacade -> KotlinClassImpl.KmPackageType(clazz, klazz, pool, km.toKmPackage())
        is KotlinClassMetadata.MultiFileClassPart -> KotlinClassImpl.KmPackageType(clazz, klazz, pool, km.toKmPackage())
        else -> throw IllegalArgumentException("Unsupported Kotlin class type: $km")
    }
}

private fun <T : Any> forJavaType(
    clazz: Class<T>,
    klazz: KClass<T>,
    pool: KClassPool
): KotlinClassImpl<T> {
    return KotlinClassImpl.KmJavaType(clazz, klazz, pool)
}

private fun <T : Any> forNativeType(clazz: Class<T>, klazz: KClass<T>, pool: KClassPool): KotlinClassImpl<T> {
    return KotlinClassImpl.KmNativeType(clazz, klazz, pool)
}

private fun <T : Any> createFrom(clazz: Class<T>, klazz: KClass<T>, pool: KClassPool): KotlinClassImpl<T> {
    return if (clazz.isNativeType) {
        forNativeType(clazz, klazz, pool)
    } else {
        clazz.getAnnotation(Metadata::class.java)
            ?.forKotlinType(clazz, klazz, pool)
            ?: forJavaType(clazz, klazz, pool)
    }
}

// This inline function works around an annoying problem with Kotlin generics!
@Suppress("nothing_to_inline")
private inline fun <T : Any> createFrom(klazz: KClass<T>, pool: KClassPool): KotlinClassImpl<T> {
    return createFrom(klazz.java, klazz, pool)
}

fun <T : Any> createFrom(clazz: Class<T>, klazz: KClass<T>): KotlinClassImpl<T> {
    val pool = mutableMapOf<KClass<*>, KotlinClassImpl<*>>(Any::class to ANY)
    for (sup in klazz.allSuperclasses) {
        pool[sup] = createFrom(sup, unmodifiableMap(pool))
    }
    return createFrom(clazz, klazz, unmodifiableMap(pool))
}
