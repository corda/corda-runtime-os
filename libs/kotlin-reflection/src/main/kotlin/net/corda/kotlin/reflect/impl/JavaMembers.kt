package net.corda.kotlin.reflect.impl

import net.corda.kotlin.reflect.kotlinJavaField
import net.corda.kotlin.reflect.kotlinJavaMethod
import net.corda.kotlin.reflect.types.JavaFunction
import net.corda.kotlin.reflect.types.KFunctionInternal
import net.corda.kotlin.reflect.types.KPropertyInternal
import net.corda.kotlin.reflect.types.KTransient
import net.corda.kotlin.reflect.types.MemberOverrideMap
import net.corda.kotlin.reflect.types.MemberSignature
import net.corda.kotlin.reflect.types.createProperty
import net.corda.kotlin.reflect.types.createStaticProperty
import net.corda.kotlin.reflect.types.declaredMemberMethods
import net.corda.kotlin.reflect.types.getterSignature
import net.corda.kotlin.reflect.types.isInheritable
import net.corda.kotlin.reflect.types.isInheritableMember
import net.corda.kotlin.reflect.types.isMember
import net.corda.kotlin.reflect.types.isStatic
import net.corda.kotlin.reflect.types.jvmSuperClasses
import net.corda.kotlin.reflect.types.memberMethods
import net.corda.kotlin.reflect.types.nameOnlySignature
import net.corda.kotlin.reflect.types.signature
import net.corda.kotlin.reflect.types.toSignature
import net.corda.kotlin.reflect.types.unionOf
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.util.LinkedList
import kotlin.reflect.KFunction
import kotlin.reflect.KProperty
import kotlin.reflect.KProperty0
import kotlin.reflect.KProperty1
import kotlin.reflect.KProperty2

class JavaMembers<T : Any>(
    clazz: Class<T>,
    allSuperKotlinClasses: List<KotlinClassImpl<*>>
) {
    val declaredProperties: Collection<KProperty1<T, *>>
    val declaredFunctions: Collection<KFunction<*>>
    val declaredStaticProperties: Collection<KProperty0<*>>
    val declaredStaticFunctions: Collection<KFunction<*>>

    val properties: Collection<KProperty1<T, *>>
        get() = declaredProperties + inheritedProperties
    val functions: Collection<KFunction<*>>
        get() = declaredFunctions + inheritedFunctions
    val extensionProperties: Collection<KProperty2<T, *, *>>
        get() = inheritedExtensionProperties
    val extensionFunctions: Collection<KFunction<*>>
        get() = inheritedExtensionFunctions
    val staticProperties: Collection<KProperty0<*>>
        get() = declaredStaticProperties + inheritedStaticProperties
    val staticFunctions: Collection<KFunction<*>>
        get() = declaredStaticFunctions + inheritedStaticFunctions

    private val inheritedProperties: Collection<KProperty1<T, *>>
    private val inheritedExtensionProperties: Collection<KProperty2<T, *, *>>
    private val inheritedFunctions: Collection<KFunction<*>>
    private val inheritedExtensionFunctions: Collection<KFunction<*>>
    private val inheritedStaticProperties: Collection<KProperty0<*>>
    private val inheritedStaticFunctions: Collection<KFunction<*>>

    init {
        // Determine which non-static, non-synthetic methods are declared by this class.
        val declaredMemberMethods = clazz.declaredMemberMethods
        val memberMethods = computeSuperMemberMethodsFor(clazz, declaredMemberMethods)

        // Determine which static, non-synthetic methods are declared by this class.
        val declaredStaticMethods = clazz.declaredMethods.filter(::isStatic)

        // Also determine which non-synthetic fields are declared by this class.
        val allDeclaredFields = clazz.declaredFields
        val declaredFields = allDeclaredFields.filter(::isMember)
        val declaredStaticFields = allDeclaredFields.filter(::isStatic)

        val superMemberProperties = allSuperKotlinClasses
            .flatMap(KotlinClassImpl<*>::declaredMemberProperties)
            .filter(::isInheritable)
            .associateByTo(MemberOverrideMap(), KProperty<*>::nameOnlySignature)
            .values
        val superMemberFunctions = allSuperKotlinClasses
            .flatMap(KotlinClassImpl<*>::declaredMemberFunctions)
            .filter(::isInheritable)
            .filterNot { it is KTransient }
            .associateByTo(MemberOverrideMap(), KFunction<*>::signature)
            .values

        val superMemberExtensionProperties = allSuperKotlinClasses
            .flatMap(KotlinClassImpl<*>::declaredMemberExtensionProperties)
            .filter(::isInheritable)
            .associateByTo(MemberOverrideMap(), KProperty<*>::getterSignature)
            .values
        val superMemberExtensionFunctions = allSuperKotlinClasses
            .flatMap(KotlinClassImpl<*>::declaredMemberExtensionFunctions)
            .filter(::isInheritable)
            .associateByTo(MemberOverrideMap(), KFunction<*>::signature)
            .values

        inheritedStaticProperties = allSuperKotlinClasses
            .flatMap(KotlinClassImpl<*>::staticProperties)
            .mapNotNull(KProperty<*>::kotlinJavaField)
            .filter(::isInheritable)
            .distinct()
            .map<Field, KProperty0<Any?>>(::createStaticProperty)
        inheritedStaticFunctions = allSuperKotlinClasses
            .flatMap(KotlinClassImpl<*>::staticFunctions)
            .mapNotNull(KFunction<*>::kotlinJavaMethod)
            .filter(::isInheritable)
            .distinct()
            .map<Method, JavaFunction<Any?>> { JavaFunction(it, clazz) }

        // Identify properties, and remove them from the list of members.
        @Suppress("unchecked_cast")
        declaredProperties = (
            superMemberProperties.extractAllBy { property ->
                (property as? KPropertyInternal<*>)
                    ?.extractDeclaredAccessorsFrom(declaredMemberMethods)
                    ?.asPropertyFor(clazz)
            } + declaredFields.map<Field, KProperty1<T, Any?>> { clazz.createProperty(it) }
            ) as Collection<KProperty1<T, *>>

        val accessors = LinkedList<KFunctionInternal<*>>()

        @Suppress("unchecked_cast")
        inheritedProperties = (
            superMemberProperties.extractAllBy { property ->
                (property as? KPropertyInternal<*>)?.populateMemberAccessorsFrom(
                    unionOf(declaredMemberMethods, memberMethods)
                )?.asPropertyFor(clazz)
            }
            ).onEach { property ->
            (property as? KPropertyInternal<*>)?.acceptIncompleteAccessors(accessors)
        } as Collection<KProperty1<T, *>>

        @Suppress("unchecked_cast")
        inheritedExtensionProperties = (
            superMemberExtensionProperties.extractAllBy { property ->
                (property as? KPropertyInternal<*>)?.populateMemberAccessorsFrom(
                    unionOf(declaredMemberMethods, memberMethods)
                )?.asPropertyFor(clazz)
            }
            ).onEach { property ->
            (property as? KPropertyInternal<*>)?.acceptJavaAccessors(accessors)
        } as Collection<KProperty2<T, *, *>>

        inheritedExtensionFunctions = superMemberExtensionFunctions.extractAllBy { function ->
            (function as? KFunctionInternal<*>)
                ?.extractMethodFrom(unionOf(declaredMemberMethods, memberMethods))
                ?.asFunctionFor(clazz, isExtension = true)
        }

        // The remaining members must all be functions.
        // Functions without Kotlin metadata must be Java ones.
        declaredFunctions = (
            superMemberFunctions.extractAllBy { function ->
                (function as? KFunctionInternal<*>)
                    ?.extractMethodFrom(declaredMemberMethods)
            } + accessors.extractAllBy { function ->
                function.captureFor(clazz)
            } + inheritedExtensionFunctions.mapNotNull { function ->
                (function as? KFunctionInternal<*>)?.captureFor(clazz)
            }
            ).map { function ->
            function.asFunctionFor(clazz, isExtension = false)
        } + declaredMemberMethods.values.map { JavaFunction(it, clazz) }

        inheritedFunctions = (
            superMemberFunctions + accessors
            ).map { function ->
            (function as? KFunctionInternal<*>)?.asFunctionFor(clazz, isExtension = false) ?: function
        }

        declaredStaticProperties = declaredStaticFields.map<Field, KProperty0<Any?>>(::createStaticProperty)
        declaredStaticFunctions = declaredStaticMethods.map<Method, JavaFunction<Any?>> { JavaFunction(it, clazz) }
    }

    private fun computeSuperMemberMethodsFor(
        clazz: Class<*>,
        declaredMemberMethods: Map<MemberSignature, Method>
    ): MutableMap<MemberSignature, Method> {
        return if (clazz.isInterface) {
            clazz.memberMethods
        } else {
            val hierarchy = clazz.jvmSuperClasses
            hierarchy += clazz
            hierarchy.flatMap { it.declaredMethods.toList() }
                .filter(::isInheritableMember)
                .associateByTo(MemberOverrideMap(), Method::toSignature)
        }.apply {
            keys.removeAll(declaredMemberMethods.keys)
        }
    }
}
