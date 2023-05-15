package net.corda.flow.application.services.interop.roundtrip

import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe
import net.corda.flow.application.services.impl.interop.dispatch.buildDispatcher
import net.corda.flow.application.services.impl.interop.facade.FacadeReaders
import net.corda.flow.application.services.impl.interop.proxies.getClientProxy
import net.corda.flow.application.services.interop.example.TokensFacade
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit
import java.util.*

class RoundTripTest {

    val facadeV1 = FacadeReaders.JSON.read(this::class.java.getResourceAsStream("/sampleFacades/tokens-facade.json")!!)
    val facadeV2 = FacadeReaders.JSON.read(this::class.java.getResourceAsStream("/sampleFacades/tokens-facade_v2.json")!!)

    val currentTime = ZonedDateTime.now()
    val server = TestTokenServer(mapOf("USD" to BigDecimal(1000000), "GBP" to BigDecimal("1000000"))) { currentTime }

    val v1Dispatcher = server.buildDispatcher(facadeV1)
    val v2Dispatcher = server.buildDispatcher(facadeV2)

    val v1Client = facadeV1.getClientProxy<TokensFacade>(v1Dispatcher)

    @Test
    fun roundtripClientProxyToServerDispatcher() {
        v1Client.getBalance("USD").result shouldBe 1000000.0

        val reservationRef = v1Client.reserveTokensV1("USD", BigDecimal(1)).result
        v1Client.getBalance("USD").result shouldBe 999999.0

        val txRef = UUID.randomUUID()
        v1Client.spendReservedTokens(reservationRef, txRef, "Peter").result
        v1Client.getBalance("USD").result shouldBe 999999.0

        server.spendHistory shouldContain Spend(
            Reservation(
                reservationRef,
                "USD",
                1.0,
                currentTime.plus(24 * 60 * 1000, ChronoUnit.MILLIS)
            ),
            txRef,
            "Peter"
        )
    }

}