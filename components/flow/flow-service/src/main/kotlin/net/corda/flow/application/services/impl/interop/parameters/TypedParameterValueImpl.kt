package net.corda.flow.application.services.impl.interop.parameters

import net.corda.v5.application.interop.parameters.TypedParameter
import net.corda.v5.application.interop.parameters.TypedParameterValue

data class TypedParameterValueImpl<T : Any>(private val parameter: TypedParameter<T>, private val value: T): TypedParameterValue<T> {
    override fun getParameter(): TypedParameter<T> {
        return parameter
    }

    override fun getValue(): T {
        return value
    }
}