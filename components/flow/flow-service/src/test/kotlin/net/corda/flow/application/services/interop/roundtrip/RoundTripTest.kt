package net.corda.flow.application.services.interop.roundtrip

import net.corda.flow.application.services.impl.interop.dispatch.buildDispatcher
import net.corda.flow.application.services.impl.interop.facade.FacadeReaders
import net.corda.flow.application.services.impl.interop.proxies.getClientProxy
import net.corda.flow.application.services.interop.example.TokensFacade
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit
import java.util.*

class RoundTripTest {
    val facadeV1 = FacadeReaders.JSON.read(this::class.java.getResourceAsStream("/sampleFacades/tokens-facade.json")!!)
    val facadeV2 =
        FacadeReaders.JSON.read(this::class.java.getResourceAsStream("/sampleFacades/tokens-facade_v2.json")!!)

    val currentTime = ZonedDateTime.now()
    val server = TestTokenServer(mapOf("USD" to BigDecimal(1000000), "GBP" to BigDecimal("1000000"))) { currentTime }

    val v1Dispatcher = server.buildDispatcher(facadeV1)
    val v2Dispatcher = server.buildDispatcher(facadeV2)

    val v1Client = facadeV1.getClientProxy<TokensFacade>(v1Dispatcher)

    @Test
    fun roundtripClientProxyToServerDispatcher() {
        assertEquals(1000000.0, v1Client.getBalance("USD").result)

        val reservationRef = v1Client.reserveTokensV1("USD", BigDecimal(1)).result
        assertEquals(999999.0, v1Client.getBalance("USD").result)

        val txRef = UUID.randomUUID()
        v1Client.spendReservedTokens(reservationRef, txRef, "Peter").result
        assertEquals(999999.0, v1Client.getBalance("USD").result)

        assertTrue(
            server.spendHistory.contains(
                Spend(
                    Reservation(
                        reservationRef,
                        "USD",
                        1.0,
                        currentTime.plus(24 * 60 * 1000, ChronoUnit.MILLIS)
                    ),
                    txRef,
                    "Peter"
                )
            )
        )
    }
}