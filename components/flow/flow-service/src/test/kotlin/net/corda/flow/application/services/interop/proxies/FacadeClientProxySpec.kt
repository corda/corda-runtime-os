package net.corda.flow.application.services.interop.proxies

import net.corda.flow.application.services.impl.interop.facade.FacadeReaders
import net.corda.flow.application.services.impl.interop.proxies.getClientProxy
import net.corda.flow.application.services.interop.example.TokenReservation
import net.corda.flow.application.services.interop.example.TokensFacade
import net.corda.flow.application.services.interop.testJsonMarshaller
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.ZonedDateTime
import java.util.*

class FacadeClientProxySpec {
    val facade = FacadeReaders.JSON.read(this::class.java.getResourceAsStream("/sampleFacades/tokens-facade_v2.json")!!)

    @Test
    fun `should convert a method call into a request, and a response into a single result value`() {
        val getBalance = facade.method("get-balance")
        val denomination = getBalance.inParameter("denomination", String::class.java)
        val balance = getBalance.outParameter("balance", BigDecimal::class.java)

        val proxy = facade.getClientProxy<TokensFacade>(testJsonMarshaller) { request ->
            assertEquals(facade.facadeId, request.facadeId)
            assertEquals(getBalance.name, request.methodName)
            assertEquals("USD", request[denomination])

            facade.response("get-balance", balance.of(BigDecimal(1000)))
        }

        assertEquals(1000.0, proxy.getBalance("USD"))
    }

    @Test
    fun `should convert a method call into a request, and a response into a data class`() {
        val reserveTokens = facade.method("reserve-tokens")
        val denomination = reserveTokens.inParameter("denomination", String::class.java)
        val amount = reserveTokens.inParameter("amount", BigDecimal::class.java)
        val timeToLive = reserveTokens.inParameter("ttl-ms", BigDecimal::class.java)

        val reservationRef = reserveTokens.outParameter("reservation-ref", UUID::class.java)
        val expirationTimestamp = reserveTokens.outParameter("expiration-timestamp", ZonedDateTime::class.java)

        val ref = UUID.randomUUID()
        val expiration = ZonedDateTime.now()

        val proxy = facade.getClientProxy<TokensFacade>(testJsonMarshaller) { request ->
            assertEquals(facade.facadeId, request.facadeId)
            assertEquals(reserveTokens.name, request.methodName)
            assertEquals("USD", request[denomination])
            assertEquals(BigDecimal(1000), request[amount])
            assertEquals(BigDecimal(5000), request[timeToLive])

            facade.response(
                "reserve-tokens",
                reservationRef.of(ref),
                expirationTimestamp.of(expiration)
            )
        }

        assertEquals(TokenReservation(ref, expiration), proxy.reserveTokensV2("USD", BigDecimal(1000), 5000L))
    }
}