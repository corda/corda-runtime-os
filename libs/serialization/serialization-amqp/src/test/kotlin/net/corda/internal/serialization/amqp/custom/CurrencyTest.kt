package net.corda.internal.serialization.amqp.custom

import net.corda.internal.serialization.amqp.ReusableSerialiseDeserializeAssert.Companion.factory
import net.corda.internal.serialization.amqp.ReusableSerialiseDeserializeAssert.Companion.serializeDeserializeAssert
import net.corda.internal.serialization.amqp.SerializationOutput
import net.corda.internal.serialization.amqp.TypeNotation
import net.corda.internal.serialization.amqp.helper.testSerializationContext
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.fail
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import java.util.Currency

class CurrencyTest {
    companion object {
        @JvmStatic
        fun allAvailableCurrencies(): Collection<Currency> = Currency.getAvailableCurrencies().sortedBy { it.currencyCode }
    }

    @ParameterizedTest
    @MethodSource("allAvailableCurrencies")
    fun allSupported(currency: Currency) {
        serializeDeserializeAssert(currency)
    }

    @Test
    fun allAvailableCurrenciesIsNotEmpty() {
        // The allSupported test will pass if the list is empty, so check we have tested something
        assertTrue(allAvailableCurrencies().isNotEmpty())
    }

    @Test
    fun testSerializerIsRegisteredForSubclass() {
        val currency = Currency.getInstance("GBP")

        val schemas = SerializationOutput(factory).serializeAndReturnSchema(currency, testSerializationContext)
        assertThat(schemas.schema.types.map(TypeNotation::name)).doesNotContain(currency::class.java.name)

        val serializer = factory.findCustomSerializer(currency::class.java, currency::class.java)
            ?: fail("No custom serializer found")
        assertThat(serializer.type).isSameAs(currency::class.java)
    }
}
