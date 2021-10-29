package net.corda.internal.serialization.amqp.custom

import net.corda.internal.serialization.amqp.CustomSerializer
import java.util.Currency

/**
 * A custom serializer for the [Currency] class, utilizing the currency code string representation.
 */
object CurrencySerializer
    : CustomSerializer.ToString<Currency>(
    Currency::class.java,
    withInheritance = false,
    maker = Currency::getInstance,
    unmaker = Currency::getCurrencyCode
)
