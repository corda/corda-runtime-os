package net.corda.internal.serialization.amqp

import com.google.common.reflect.TypeToken
import net.corda.internal.serialization.model.TypeIdentifier
import net.corda.sandbox.SandboxGroup
import net.corda.serialization.SerializationContext
import net.corda.v5.base.annotations.CordaSerializable
import org.apache.qpid.proton.codec.Data
import java.io.NotSerializableException
import java.lang.reflect.GenericArrayType
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type
import java.lang.reflect.TypeVariable
import java.lang.reflect.WildcardType

/**
 * Extension helper for writing described objects.
 */
fun Data.withDescribed(descriptor: Descriptor, block: Data.() -> Unit) {
    // Write described
    putDescribed()
    enter()
    // Write descriptor
    putObject(descriptor.code ?: descriptor.name)
    block()
    exit() // exit described
}

/**
 * Extension helper for writing lists.
 */
fun Data.withList(block: Data.() -> Unit) {
    // Write list
    putList()
    enter()
    block()
    exit() // exit list
}

/**
 * Extension helper for outputting reference to already observed object
 */
fun Data.writeReferencedObject(refObject: ReferencedObject) {
    // Write described
    putDescribed()
    enter()
    // Write descriptor
    putObject(refObject.descriptor)
    putUnsignedInteger(refObject.described)
    exit() // exit described
}

fun resolveTypeVariables(actualType: Type, contextType: Type?, sandboxGroup: SandboxGroup): Type {
    val resolvedType = if (contextType != null) TypeToken.of(contextType).resolveType(actualType).type else actualType
    // TODO: surely we check it is concrete at this point with no TypeVariables
    return if (resolvedType is TypeVariable<*>) {
        val bounds = resolvedType.bounds
        return if (bounds.isEmpty()) {
            TypeIdentifier.UnknownType.getLocalType(sandboxGroup)
        } else if (bounds.size == 1) {
            resolveTypeVariables(bounds[0], contextType, sandboxGroup)
        } else throw AMQPNotSerializableException(
                actualType,
                "Got bounded type $actualType but only support single bound.")
    } else {
        resolvedType
    }
}

internal fun Type.asClass(): Class<*> {
    return when(this) {
        is Class<*> -> this
        is ParameterizedType -> this.rawType.asClass()
        is GenericArrayType -> this.genericComponentType.asClass().arrayClass()
        is TypeVariable<*> -> this.bounds.first().asClass()
        is WildcardType -> this.upperBounds.first().asClass()
        // Per https://docs.oracle.com/javase/8/docs/api/java/lang/reflect/Type.html,
        // there is nothing else that it can be, so this can never happen.
        else -> throw UnsupportedOperationException("Cannot convert $this to class")
    }
}

internal fun Type.asArray(sandboxGroup: SandboxGroup): Type? {
    return when(this) {
        is Class<*>,
        is ParameterizedType -> TypeIdentifier.ArrayOf(TypeIdentifier.forGenericType(this)).getLocalType(sandboxGroup)
        else -> null
    }
}

internal fun Class<*>.arrayClass(): Class<*> = java.lang.reflect.Array.newInstance(this, 0).javaClass

internal fun Type.isArray(): Boolean = (this is Class<*> && this.isArray) || (this is GenericArrayType)

internal fun Type.componentType(): Type {
    check(this.isArray()) { "$this is not an array type." }
    return (this as? Class<*>)?.componentType ?: (this as GenericArrayType).genericComponentType
}

internal fun Class<*>.asParameterizedType(sandboxGroup: SandboxGroup): ParameterizedType =
    TypeIdentifier.Erased(this.name, this.typeParameters.size)
            .toParameterized(this.typeParameters.map { TypeIdentifier.forGenericType(it) })
            .getLocalType(sandboxGroup) as ParameterizedType

internal fun Type.asParameterizedType(sandboxGroup: SandboxGroup): ParameterizedType {
    return when (this) {
        is Class<*> -> this.asParameterizedType(sandboxGroup)
        is ParameterizedType -> this
        else -> throw AMQPNotSerializableException(this, "Don't know how to convert to ParameterizedType")
    }
}

internal fun Type.isSubClassOf(type: Type): Boolean {
    return TypeToken.of(this).isSubtypeOf(TypeToken.of(type).rawType)
}

fun requireWhitelisted(type: Type) {
    // See CORDA-2782 for explanation of the special exemption made for Comparable
    if (!isWhitelisted(type.asClass()) && type.asClass() != java.lang.Comparable::class.java) {
        throw AMQPNotSerializableException(
                type,
                "Class \"$type\" is not on the whitelist or annotated with @CordaSerializable.")
    }
}

fun isWhitelisted(clazz: Class<*>) = hasCordaSerializable(clazz)
fun isNotWhitelisted(clazz: Class<*>) = !isWhitelisted(clazz)

/**
 * Check the given [Class] has the [CordaSerializable] annotation, either directly or inherited from any of its super
 * classes or interfaces.
 */
fun hasCordaSerializable(type: Class<*>): Boolean {
    return type.isAnnotationPresent(CordaSerializable::class.java)
            || type.interfaces.any(::hasCordaSerializable)
            || (type.superclass != null && hasCordaSerializable(type.superclass))
}

fun SerializationContext.currentSandboxGroup(): SandboxGroup = sandboxGroup as? SandboxGroup
    ?: throw NotSerializableException("sandboxGroup is not set in serialization context")
