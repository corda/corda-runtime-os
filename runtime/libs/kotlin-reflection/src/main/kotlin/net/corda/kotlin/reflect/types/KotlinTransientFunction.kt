package net.corda.kotlin.reflect.types

import kotlinx.metadata.Flag.Function.IS_EXTERNAL
import kotlinx.metadata.Flag.Function.IS_INFIX
import kotlinx.metadata.Flag.Function.IS_INLINE
import kotlinx.metadata.Flag.Function.IS_OPERATOR
import kotlinx.metadata.Flag.Function.IS_SUSPEND
import kotlinx.metadata.Flags
import java.lang.reflect.Method
import java.util.Objects
import kotlin.reflect.KParameter
import kotlin.reflect.KType
import kotlin.reflect.KTypeParameter
import kotlin.reflect.KVisibility

@Suppress("LongParameterList")
class KotlinTransientFunction<V>(
    override val name: String,
    private val flags: Flags,
    override val returnType: KType,
    override val javaMethod: Method,
    private val instanceClass: Class<*>,
    private val isExtension: Boolean
) : KFunctionInternal<V>, Function<V>, KTransient {
    override val isAbstract: Boolean
        get() = isAbstract(javaMethod, flags)
    override val isFinal: Boolean
        get() = isFinal(javaMethod, flags)
    override val isOpen: Boolean
        get() = isOpen(javaMethod, flags)
    override val isExternal: Boolean
        get() = IS_EXTERNAL(flags)
    override val isInfix: Boolean
        get() = IS_INFIX(flags)
    override val isInline: Boolean
        get() = IS_INLINE(flags)
    override val isOperator: Boolean
        get() = IS_OPERATOR(flags)
    override val isSuspend: Boolean
        get() = IS_SUSPEND(flags)

    override val annotations: List<Annotation>
        get() = TODO("Not yet implemented")
    override val parameters: List<KParameter>
        get() = javaMethod.createParameters(instanceClass, isExtension, emptyList())
    override val typeParameters: List<KTypeParameter>
        get() = TODO("Not yet implemented")
    override val visibility: KVisibility?
        get() = getVisibility(flags)

    override val signature: MemberSignature
        get() = javaMethod.toSignature()

    override fun asFunctionFor(instanceClass: Class<*>, isExtension: Boolean) = KotlinTransientFunction<V>(
        name = name,
        flags = flags,
        returnType = returnType,
        javaMethod = javaMethod,
        instanceClass = instanceClass,
        isExtension = isExtension
    )

    override fun withJavaMethod(method: Method) = KotlinTransientFunction<V>(
        name = method.name,
        flags = flags,
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
