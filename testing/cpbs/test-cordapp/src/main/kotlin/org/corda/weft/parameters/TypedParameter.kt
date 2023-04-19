package org.corda.weft.parameters

import java.math.BigDecimal
import java.nio.ByteBuffer


/**
 * The value of a [TypedParameter].
 *
 * Cannot be constructed unless the types of the parameter and value agree.
 *
 * @param parameter The parameter to which this value belongs.
 * @value The value of the parameter.
 */
data class TypedParameterValue<T : Any>(val parameter: TypedParameter<T>, val value: T)


/**
 * A [TypedParameter] is a parameter having a name and a defined [ParameterType].
 *
 * @param name The name of the parameter, e.g. "amount".
 * @param type The type of the parameter, e.g. [ParameterType.StringType].
 */
data class TypedParameter<T : Any>(val name: String, val type: ParameterType<T>) {
    infix fun of(value: T): TypedParameterValue<T> = TypedParameterValue(this, value)
}

/**
 * (Kotlin-only) utility function for automatic conversion of [Int] to [BigDecimal].
 */
infix fun TypedParameter<BigDecimal>.of(intValue: Int) = of(BigDecimal(intValue))

/**
 * (Kotlin-only) utility function for automatic conversion of [Long] to [BigDecimal].
 */
infix fun TypedParameter<BigDecimal>.of(longValue: Long) = of(BigDecimal(longValue))

/**
 * (Kotlin-only) utility function for automatic conversion of [String] to [BigDecimal].
 */
infix fun TypedParameter<BigDecimal>.of(stringValue: String) = of(BigDecimal(stringValue))

/**
 * (Kotlin-only) utility function for automatic conversion of [ByteArray] to [ByteBuffer].
 */
infix fun TypedParameter<ByteBuffer>.of(bytes: ByteArray) = of(ByteBuffer.wrap(bytes))
