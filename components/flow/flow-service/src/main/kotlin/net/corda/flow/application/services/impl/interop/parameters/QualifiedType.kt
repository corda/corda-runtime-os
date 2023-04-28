package net.corda.flow.application.services.impl.interop.parameters

import net.corda.v5.application.interop.parameters.ParameterType
import net.corda.v5.application.interop.parameters.ParameterTypeLabel
import net.corda.v5.application.interop.parameters.TypeQualifier

@Suppress("UNCHECKED_CAST")
data class QualifiedType<T>(private val rawParameterType: ParameterType<T>, private val qualifier: TypeQualifier) : ParameterType<T> {
    private val expectedRawClass: Class<T> = rawParameterType.expectedType
    private val expectedClass: Class<T> = typeLabel.expectedClass as Class<T>
    private val isQualified: Boolean = true

    override fun getTypeLabel(): ParameterTypeLabel {
        return typeLabel
    }

    override fun getExpectedType(): Class<T> {
        return rawParameterType.expectedType
    }

    override fun isQualified(): Boolean {
        return isQualified
    }

    override fun getQualifier(): TypeQualifier {
        return qualifier
    }

    override fun getRawParameterType(): ParameterType<T> {
        return rawParameterType
    }

    fun getExpectedRawClass(): Class<T> {
        return expectedRawClass
    }

    fun getExpectedClass(): Class<T> {
        return expectedClass
    }

    override fun toString(): String {
        return "$rawParameterType ($qualifier)"
    }
}