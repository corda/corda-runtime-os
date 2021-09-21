package net.corda.internal.serialization.amqp.custom

import net.corda.internal.serialization.amqp.ReusableSerialiseDeserializeAssert.Companion.serializeDeserializeAssert
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import java.util.Currency
import kotlin.test.assertTrue

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
}
