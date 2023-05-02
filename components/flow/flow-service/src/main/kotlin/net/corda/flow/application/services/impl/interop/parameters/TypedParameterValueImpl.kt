package net.corda.flow.application.services.impl.interop.parameters

import net.corda.v5.application.interop.parameters.TypedParameter
import net.corda.v5.application.interop.parameters.TypedParameterValue

/**
 * The value of a [TypedParameter].
 *
 * Cannot be constructed unless the types of the parameter and value agree.
 *
 * @param parameter The parameter to which this value belongs.
 * @value The value of the parameter.
 */
data class TypedParameterValueImpl<T : Any>(private val parameter: TypedParameter<T>, private val value: T): TypedParameterValue<T> {
    override fun getParameter(): TypedParameter<T> {
        return parameter
    }

    override fun getValue(): T {
        return value
    }
}