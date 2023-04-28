package net.corda.flow.application.services.interop.dispatchers

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import net.corda.flow.application.services.impl.interop.dispatch.buildDispatcher
import net.corda.flow.application.services.impl.interop.facade.FacadeReaders
import net.corda.flow.application.services.interop.example.TokenReservation
import net.corda.flow.application.services.interop.example.TokensFacade
import net.corda.v5.application.interop.binding.InteropAction
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import java.math.BigDecimal
import java.time.ZonedDateTime
import java.util.*

class FacadeServerDispatcherSpec : DescribeSpec({

    val facadeV1 = FacadeReaders.JSON.read(this::class.java.getResourceAsStream("/tokens-facade.json")!!)
    val facadeV2 = FacadeReaders.JSON.read(this::class.java.getResourceAsStream("/tokens-facade_v2.json")!!)

    val reservationRef = UUID.randomUUID()
    val expirationTimestamp = ZonedDateTime.now()

    val mockServer = mock<TokensFacade> {
        on { releaseReservedTokens(any()) } doReturn InteropAction.ServerResponse(Unit)
        on { reserveTokensV2(any(), any(), any()) } doReturn
                InteropAction.ServerResponse(TokenReservation(reservationRef, expirationTimestamp))
    }

    val v1Dispatcher = mockServer.buildDispatcher(facadeV1)
    val v2Dispatcher = mockServer.buildDispatcher(facadeV2)

    describe("A facade server dispatcher") {
        it("should dispatch a request to the matching method on the server object") {
            val releaseReservedTokens = facadeV1.method("release-reserved-tokens")
            val reservationRefParam = releaseReservedTokens.inParameter("reservation-ref", UUID::class.java)

            val request = releaseReservedTokens.request(reservationRefParam.of(reservationRef))
            val response = v1Dispatcher(request)

            verify(mockServer).releaseReservedTokens(reservationRef)

            response.methodName shouldBe "release-reserved-tokens"
        }

        it("should map data class return values to out parameters") {
            val reserveTokensV2 = facadeV2.method("reserve-tokens")
            val denominationIn = reserveTokensV2.inParameter("denomination", String::class.java)
            val amountIn = reserveTokensV2.inParameter("amount", BigDecimal::class.java)
            val ttlMsIn = reserveTokensV2.inParameter("ttl-ms", BigDecimal::class.java)

            val reservationRefOut = reserveTokensV2.outParameter("reservation-ref", UUID::class.java)
            val expirationTimestampOut = reserveTokensV2.outParameter("expiration-timestamp", ZonedDateTime::class.java)

            // Construct a request the hard way
            val request = reserveTokensV2.request(
                denominationIn.of("USD"),
                amountIn.of(BigDecimal(1000)),
                ttlMsIn.of(BigDecimal(100))
            )

            // Dispatch it to a server object to get a response
            val response = v2Dispatcher(request)

            // Verify that the server object was called with the expected parameters
            verify(mockServer).reserveTokensV2("USD", BigDecimal(1000), 100)

            // Verify that the response was correctly unpacked into out-parameters
            response[reservationRefOut] shouldBe reservationRef
            response[expirationTimestampOut] shouldBe expirationTimestamp
        }
    }
})