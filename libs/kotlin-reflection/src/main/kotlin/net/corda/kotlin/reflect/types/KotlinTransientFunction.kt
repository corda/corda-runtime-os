package net.corda.kotlin.reflect.types

import java.lang.reflect.Method
import java.util.Objects
import kotlin.reflect.KParameter
import kotlin.reflect.KType
import kotlin.reflect.KTypeParameter
import kotlin.reflect.KVisibility
import kotlinx.metadata.KmPropertyAccessorAttributes
import kotlinx.metadata.isExternal
import kotlinx.metadata.isInline
import kotlinx.metadata.modality
import kotlinx.metadata.visibility

@Suppress("LongParameterList")
class KotlinTransientFunction<V>(
    override val name: String,
    private val attributes: KmPropertyAccessorAttributes,
    override val returnType: KType,
    override val javaMethod: Method,
    private val instanceClass: Class<*>,
    private val isExtension: Boolean
) : KFunctionInternal<V>, Function<V>, KTransient {
    override val isAbstract: Boolean
        get() = isAbstract(javaMethod, attributes.modality)
    override val isFinal: Boolean
        get() = isFinal(javaMethod, attributes.modality)
    override val isOpen: Boolean
        get() = isOpen(javaMethod, attributes.modality)
    override val isExternal: Boolean
        get() = attributes.isExternal
    override val isInfix: Boolean
        get() = false
    override val isInline: Boolean
        get() = attributes.isInline
    override val isOperator: Boolean
        get() = false
    override val isSuspend: Boolean
        get() = false

    override val annotations: List<Annotation>
        get() = TODO("Not yet implemented")
    override val parameters: List<KParameter>
        get() = javaMethod.createParameters(instanceClass, isExtension, emptyList())
    override val typeParameters: List<KTypeParameter>
        get() = TODO("Not yet implemented")
    override val visibility: KVisibility?
        get() = getVisibility(attributes.visibility)

    override val signature: MemberSignature
        get() = javaMethod.toSignature()

    override fun asFunctionFor(instanceClass: Class<*>, isExtension: Boolean) = KotlinTransientFunction<V>(
        name = name,
        attributes = attributes,
        returnType = returnType,
        javaMethod = javaMethod,
        instanceClass = instanceClass,
        isExtension = isExtension
    )

    override fun withJavaMethod(method: Method) = KotlinTransientFunction<V>(
        name = method.name,
        attributes = attributes,
        returnType = method.returnType.createKType(returnType.isMarkedNullable),
        javaMethod = method,
        instanceClass = instanceClass,
        isExtension = isExtension
    )

    override fun call(vararg args: Any?): V {
        TODO("Not yet implemented")
    }

    override fun callBy(args: Map<KParameter, Any?>): V {
        TODO("Not yet implemented")
    }

    override fun equals(other: Any?): Boolean {
        return when {
            other === this -> true
            other !is KotlinTransientFunction<*> -> false
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
