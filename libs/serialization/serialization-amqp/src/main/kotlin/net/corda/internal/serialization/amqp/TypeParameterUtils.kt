package net.corda.internal.serialization.amqp

import com.google.common.reflect.TypeResolver
import net.corda.sandbox.SandboxGroup
import java.lang.reflect.GenericArrayType
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type

/**
 * Try and infer concrete types for any generics type variables for the actual class encountered,
 * based on the declared type.
 */
fun inferTypeVariables(actualClass: Class<*>,
                       declaredClass: Class<*>,
                       declaredType: Type,
                       sandboxGroup: SandboxGroup): Type? = when (declaredType) {
    is ParameterizedType -> inferTypeVariables(actualClass, declaredClass, declaredType, sandboxGroup)
    is GenericArrayType -> {
        val declaredComponent = declaredType.genericComponentType
        inferTypeVariables(actualClass.componentType, declaredComponent.asClass(), declaredComponent, sandboxGroup)?.asArray(sandboxGroup)
    }
    // Nothing to infer, otherwise we'd have ParameterizedType
    else -> actualClass
}

/**
 * Try and infer concrete types for any generics type variables for the actual class encountered, based on the declared
 * type, which must be a [ParameterizedType].
 */
private fun inferTypeVariables(actualClass: Class<*>, declaredClass: Class<*>, declaredType: ParameterizedType, sandboxGroup: SandboxGroup): Type? {
    if (declaredClass == actualClass) {
        return null
    }

    if (actualClass.typeParameters.isEmpty()) {
        return actualClass
    }
    // The actual class can never have type variables resolved, due to the JVM's use of type erasure, so let's try and resolve them
    // Search for declared type in the inheritance hierarchy and then see if that fills in all the variables
    val implementationChain: List<Type> = findPathToDeclared(actualClass, declaredType, sandboxGroup)?.toList()
            ?: throw AMQPNotSerializableException(
                    declaredType,
                    "No inheritance path between actual $actualClass and declared $declaredType.")

    val start = implementationChain.last()
    val rest = implementationChain.dropLast(1).drop(1)
    val resolver = rest.reversed().fold(TypeResolver().where(start, declaredType)) { resolved, chainEntry ->
        val newResolved = resolved.resolveType(chainEntry)
        TypeResolver().where(chainEntry, newResolved)
    }
    // The end type is a special case as it is a Class, so we need to fake up a ParameterizedType for it to get
    // the TypeResolver to do anything.
    val endType = actualClass.asParameterizedType(sandboxGroup)
    return resolver.resolveType(endType)
}

// Stop when reach declared type or return null if we don't find it.
private fun findPathToDeclared(startingType: Type, declaredType: Type, sandboxGroup: SandboxGroup, chain: Sequence<Type> = emptySequence()): Sequence<Type>? {
    val extendedChain = chain + startingType
    val startingClass = startingType.asClass()

    if (startingClass == declaredType.asClass()) {
        // We're done...
        return extendedChain
    }

    val resolver = { type: Type ->
        TypeResolver().where(
                startingClass.asParameterizedType(sandboxGroup),
                startingType.asParameterizedType(sandboxGroup)
        )
                .resolveType(type)
    }

    // Now explore potential options of superclass and all interfaces
    return findPathViaGenericSuperclass(startingClass, resolver, declaredType, extendedChain, sandboxGroup)
        ?: findPathViaInterfaces(startingClass, resolver, declaredType, extendedChain, sandboxGroup)
}

private fun findPathViaInterfaces(startingClass: Class<*>, resolver: (Type) -> Type, declaredType: Type, extendedChain: Sequence<Type>, sandboxGroup: SandboxGroup): Sequence<Type>? =
    startingClass.genericInterfaces.asSequence().map {
        findPathToDeclared(resolver(it), declaredType, sandboxGroup, extendedChain)
    }.filterNotNull().firstOrNull()


private fun findPathViaGenericSuperclass(startingClass: Class<*>, resolver: (Type) -> Type, declaredType: Type, extendedChain: Sequence<Type>, sandboxGroup: SandboxGroup): Sequence<Type>? {
    val superClass = startingClass.genericSuperclass ?: return null
    return findPathToDeclared(resolver(superClass), declaredType, sandboxGroup, extendedChain)
}

