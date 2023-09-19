package net.corda.kotlin.reflect.types

import java.lang.reflect.Method
import java.util.Objects
import kotlin.contracts.ExperimentalContracts
import kotlin.reflect.KParameter
import kotlin.reflect.KType
import kotlin.reflect.KTypeParameter
import kotlin.reflect.KVisibility
import kotlinx.metadata.KmFunction
import kotlinx.metadata.isExternal
import kotlinx.metadata.isInfix
import kotlinx.metadata.isInline
import kotlinx.metadata.isNullable
import kotlinx.metadata.isOperator
import kotlinx.metadata.isSuspend
import kotlinx.metadata.jvm.lambdaClassOriginName
import kotlinx.metadata.jvm.signature
import kotlinx.metadata.modality
import kotlinx.metadata.visibility

class KotlinFunction<V> private constructor(
    private val kmFunction: KmFunction,
    override val signature: MemberSignature?,
    override val javaMethod: Method?,
    private val instanceClass: Class<*>,
    private val isExtension: Boolean
) : KFunctionInternal<V>, Function<V> {
    constructor(function: KmFunction, declaringClass: Class<*>) : this(
        kmFunction = function,
        signature = function.signature?.toSignature(declaringClass.classLoader),
        javaMethod = null,
        instanceClass = declaringClass,
        isExtension = isExtension(function)
    )

    override val isAbstract: Boolean
        get() = isAbstract(javaMethod, kmFunction.modality)
    override val isFinal: Boolean
        get() = isFinal(javaMethod, kmFunction.modality)
    override val isOpen: Boolean
        get() = isOpen(javaMethod, kmFunction.modality)
    override val isExternal: Boolean
        get() = kmFunction.isExternal
    override val isInfix: Boolean
        get() = kmFunction.isInfix
    override val isInline: Boolean
        get() = kmFunction.isInline
    override val isOperator: Boolean
        get() = kmFunction.isOperator
    override val isSuspend: Boolean
        get() = kmFunction.isSuspend

    override val visibility: KVisibility?
        get() = getVisibility(kmFunction.visibility)

    override val annotations: List<Annotation>
        get() = TODO("KotlinFunction.annotations: Not yet implemented")
    override val name: String
        get() = kmFunction.name
    override val parameters: List<KParameter>
        get() = javaMethod?.createParameters(instanceClass, isExtension, kmFunction.valueParameters) ?: emptyList()
    override val returnType: KType
        get() = javaMethod?.returnType?.createKType(kmFunction.returnType.isNullable)
                    ?: KotlinType(kmFunction.returnType)
    override val typeParameters: List<KTypeParameter>
        get() = kmFunction.typeParameters.map(::KotlinTypeParameter)

    override fun asFunctionFor(instanceClass: Class<*>, isExtension: Boolean): KotlinFunction<V> {
        val newFunc = if (isExtension != this.isExtension) {
            // We are "recasting" this extension function as an ordinary function
            // (or vice versa), which means that the original "value parameter"
            // information no longer applies. So create a new KmFunction object
            // without any value parameters.
            @Suppress("deprecation")
            KmFunction(kmFunction.name).also { kmf ->
                kmf.flags = kmFunction.flags
                // Copy values from KmFunction.
                kmf.receiverParameterType = kmFunction.receiverParameterType
                kmf.versionRequirements += kmFunction.versionRequirements
                kmf.typeParameters += kmFunction.typeParameters
                kmf.returnType = kmFunction.returnType
                @OptIn(ExperimentalContracts::class)
                kmf.contract = kmFunction.contract

                // Copy values from JvmFunctionExtension.
                kmf.lambdaClassOriginName = kmFunction.lambdaClassOriginName
                kmf.signature = kmFunction.signature
            }
        } else {
            kmFunction
        }
        return KotlinFunction(newFunc, signature, javaMethod, instanceClass, isExtension)
    }

    override fun withJavaMethod(method: Method)
        = KotlinFunction<V>(kmFunction, method.toSignature(), method, instanceClass, isExtension)

    override fun call(vararg args: Any?): V {
        TODO("Not yet implemented")
    }

    override fun callBy(args: Map<KParameter, Any?>): V {
        TODO("Not yet implemented")
    }

    override fun equals(other: Any?): Boolean {
        return when {
            other === this -> true
            other !is KotlinFunction<*> -> false
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
