package net.corda.kotlin.reflect.types

import java.lang.reflect.Method
import java.util.Objects
import kotlin.reflect.KParameter
import kotlin.reflect.KType
import kotlin.reflect.KTypeParameter
import kotlin.reflect.KVisibility
import kotlin.reflect.full.starProjectedType

class JavaFunction<V>(
    override val javaMethod: Method,
    private val instanceClass: Class<*>
) : KFunctionInternal<V>, Function<V> {
    override val annotations: List<Annotation>
        get() = TODO("Not yet implemented")
    override val isAbstract: Boolean
        get() = isAbstract(javaMethod)
    override val isFinal: Boolean
        get() = isFinal(javaMethod)
    override val isOpen: Boolean
        get() = isOpen(javaMethod)
    override val isExternal: Boolean
        get() = false
    override val isInfix: Boolean
        get() = false
    override val isInline: Boolean
        get() = false
    override val isOperator: Boolean
        get() = false
    override val isSuspend: Boolean
        get() = false
    override val name: String
        get() = javaMethod.name
    override val parameters: List<KParameter>
        get() = javaMethod.createParameters(instanceClass, isExtension = false, emptyList())
    override val returnType: KType
        get() = javaMethod.returnType.kotlin.starProjectedType
    override val typeParameters: List<KTypeParameter>
        get() = TODO("Not yet implemented")
    override val visibility: KVisibility?
        get() = getVisibility(javaMethod)

    override val signature: MemberSignature
        get() = javaMethod.toSignature()

    override fun asFunctionFor(instanceClass: Class<*>, isExtension: Boolean) = JavaFunction<V>(javaMethod, instanceClass)
    override fun withJavaMethod(method: Method) = JavaFunction<V>(method, instanceClass)

    override fun call(vararg args: Any?): V {
        TODO("JavaFunction.call(): Not yet implemented")
    }

    override fun callBy(args: Map<KParameter, Any?>): V {
        TODO("JavaFunction.callBy(): Not yet implemented")
    }

    override fun equals(other: Any?): Boolean {
        return when {
            other === this -> true
            other !is JavaFunction<*> -> false
            else -> name == other.name && javaMethod == other.javaMethod
        }
    }

    override fun hashCode(): Int {
        return Objects.hash(name, javaMethod)
    }

    override fun toString(): String {
        return "fun $name: $javaMethod"
    }
}
