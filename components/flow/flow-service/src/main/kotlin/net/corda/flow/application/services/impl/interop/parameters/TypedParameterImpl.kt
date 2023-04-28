package net.corda.flow.application.services.impl.interop.parameters

import net.corda.v5.application.interop.parameters.ParameterType
import net.corda.v5.application.interop.parameters.TypedParameter
import net.corda.v5.application.interop.parameters.TypedParameterValue

data class TypedParameterImpl<T : Any>(private val name: String, private val type: ParameterType<T>): TypedParameter<T> {
    override fun getName(): String {
        return name
    }

    override fun getType(): ParameterType<T> {
        return type
    }

    override fun of(value: T): TypedParameterValue<T> = TypedParameterValueImpl(this, value)
}