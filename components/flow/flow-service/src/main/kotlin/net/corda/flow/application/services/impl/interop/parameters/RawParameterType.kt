package net.corda.flow.application.services.impl.interop.parameters

import net.corda.v5.application.interop.parameters.ParameterType
import net.corda.v5.application.interop.parameters.ParameterTypeLabel
import net.corda.v5.application.interop.parameters.TypeQualifier

data class RawParameterType<T>(val typeLabel: ParameterTypeLabel) : ParameterType<T> {
    val expectedType: Class<T> get() = typeLabel.expectedClass as Class<T>
    val isQualified: Boolean = false
    val qualifier: TypeQualifier? = null
    override fun getTypeLabel(): ParameterTypeLabel {
        return typeLabel
    }

    override fun getExpectedType(): Class<T> {
        return expectedType
    }

    override fun isQualified(): Boolean {
        return isQualified
    }

    override fun getQualifier(): TypeQualifier? {
        return qualifier
    }

    override fun getRawParameterType(): ParameterType<T> {
        return expectedType as ParameterType<T>
    }
}