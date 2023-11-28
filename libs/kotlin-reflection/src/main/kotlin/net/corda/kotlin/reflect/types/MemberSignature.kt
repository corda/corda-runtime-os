package net.corda.kotlin.reflect.types

import java.util.Objects

class MemberSignature(
    val name: String,
    val returnType: Class<*>,
    val parameterTypes: Array<Class<*>>
) {
    fun isAssignableFrom(signature: MemberSignature): Boolean {
        return name == signature.name &&
            returnType.isAssignableFrom(signature.returnType) &&
            parameterTypes.contentEquals(signature.parameterTypes)
    }

    override fun hashCode(): Int {
        return Objects.hash(name, returnType) * 31 + parameterTypes.contentHashCode()
    }

    override fun equals(other: Any?): Boolean {
        return when {
            this === other -> true
            other !is MemberSignature -> false
            else -> parameterTypes.contentEquals(other.parameterTypes) &&
                returnType === other.returnType &&
                name == other.name
        }
    }

    override fun toString(): String {
        return "${returnType.name} $name(${parameterTypes.joinToString(",", transform = Class<*>::getName)})"
    }
}
