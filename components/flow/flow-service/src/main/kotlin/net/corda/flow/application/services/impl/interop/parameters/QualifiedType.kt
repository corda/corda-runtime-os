package net.corda.flow.application.services.impl.interop.parameters

import net.corda.v5.application.interop.parameters.ParameterType
import net.corda.v5.application.interop.parameters.ParameterTypeLabel
import net.corda.v5.application.interop.parameters.TypeQualifier

data class QualifiedType<T>(val rawParameterType: RawParameterType<T>, val qualifier: TypeQualifier) : ParameterType<T> {
    val expectedRawClass: Class<T> get() = rawParameterType.expectedType
    val expectedClass: Class<T> get() = typeLabel.expectedClass as Class<T>
    val isQualified: Boolean = true

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