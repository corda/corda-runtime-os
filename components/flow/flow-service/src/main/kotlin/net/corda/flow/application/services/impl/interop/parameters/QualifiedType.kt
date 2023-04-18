package net.corda.flow.application.services.impl.interop.parameters

import net.corda.v5.application.interop.parameters.ParameterType
import net.corda.v5.application.interop.parameters.TypeQualifier

data class QualifiedType<T>(
    val type: TypeParameters<T>,
    val qualifier: TypeQualifier
) : ParameterType<T>() {
    val expectedType: Class<T> get() = type.expectedType
    override fun toString() = "$type ($qualifier)"
}