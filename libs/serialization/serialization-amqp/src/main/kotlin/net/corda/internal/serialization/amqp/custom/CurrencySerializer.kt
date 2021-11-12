package net.corda.internal.serialization.amqp.custom

import net.corda.serialization.BaseDirectSerializer
import net.corda.serialization.InternalDirectSerializer.ReadObject
import net.corda.serialization.InternalDirectSerializer.WriteObject
import java.util.Currency

/**
 * A custom serializer for the [Currency] class, utilizing the currency code string representation.
 */
class CurrencySerializer : BaseDirectSerializer<Currency>() {
    override val type: Class<Currency> get() = Currency::class.java
    override val withInheritance: Boolean get() = false

    override fun readObject(reader: ReadObject): Currency {
        return Currency.getInstance(reader.getAs(String::class.java))
    }

    override fun writeObject(obj: Currency, writer: WriteObject) {
        writer.putAsString(obj.currencyCode)
    }
}
