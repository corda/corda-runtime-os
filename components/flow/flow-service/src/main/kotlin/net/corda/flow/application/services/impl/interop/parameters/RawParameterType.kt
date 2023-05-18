package net.corda.flow.application.services.impl.interop.parameters

import net.corda.v5.application.interop.parameters.ParameterType
import net.corda.v5.application.interop.parameters.ParameterTypeLabel
import net.corda.v5.application.interop.parameters.TypeQualifier

@Suppress("UNCHECKED_CAST")
data class RawParameterType<T>(private val typeLabel: ParameterTypeLabel) : ParameterType<T> {
    private val expectedType: Class<T> = typeLabel.expectedClass as Class<T>
    private val isQualified: Boolean = false
    private val qualifier: TypeQualifier? = null
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
        return expectedType as ParameterType<T> //expectedType is Class<?/T> vs ParameterType<T>
    }

    override fun toString(): String {
        return typeLabel.typeName
    }
}