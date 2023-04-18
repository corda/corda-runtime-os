package net.corda.flow.application.services.impl.interop.parameters

import net.corda.v5.application.interop.parameters.ParameterType
import net.corda.v5.application.interop.parameters.ParameterTypeLabel
import net.corda.v5.application.interop.parameters.TypeQualifier

data class RawParameterType<T>(val typeLabel: ParameterTypeLabel) : ParameterType<T> {
    val expectedClass: Class<T> get() = typeLabel.expectedClass as Class<T>
    val isQualified: Boolean = false
    val qualifier: TypeQualifier? = null
    override fun getTypeLabel(): ParameterTypeLabel {
        TODO("Not yet implemented")
    }

    override fun getExpectedType(): Class<T> {
        TODO("Not yet implemented")
    }

    override fun isQualified(): Boolean {
        TODO("Not yet implemented")
    }

    override fun getQualifier(): TypeQualifier {
        TODO("Not yet implemented")
    }
}