package net.corda.internal.serialization.amqp.custom

import net.corda.v5.serialization.SerializationCustomSerializer
import java.util.Currency

/**
 * A custom serializer for the [Currency] class, utilizing the currency code string representation.
 */
object CurrencySerializer : SerializationCustomSerializer<Currency, String>{
    override fun toProxy(obj: Currency): String = obj.currencyCode
    override fun fromProxy(proxy: String): Currency = Currency.getInstance(proxy)
}
