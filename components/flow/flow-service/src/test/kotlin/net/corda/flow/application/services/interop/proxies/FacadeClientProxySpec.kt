package net.corda.flow.application.services.interop.proxies

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.doubles.shouldBeExactly
import io.kotest.matchers.shouldBe
import net.corda.flow.application.services.impl.interop.facade.FacadeReaders
import net.corda.flow.application.services.impl.interop.proxies.getClientProxy
import net.corda.flow.application.services.interop.example.TokenReservation
import net.corda.flow.application.services.interop.example.TokensFacade
import java.math.BigDecimal
import java.time.ZonedDateTime
import java.util.*

class FacadeClientProxySpec : DescribeSpec({

    val facade = FacadeReaders.JSON.read(this::class.java.getResourceAsStream("/sampleFacades/tokens-facade_v2.json")!!)

    describe("A facade client proxy") {

        it("should convert a method call into a request, and a response into a single result value") {
            val getBalance = facade.method("get-balance")
            val denomination = getBalance.inParameter("denomination", String::class.java)
            val balance = getBalance.outParameter("balance", BigDecimal::class.java)

            val proxy = facade.getClientProxy<TokensFacade> { request ->
                request.facadeId shouldBe facade.facadeId
                request.methodName shouldBe getBalance.name
                request[denomination] shouldBe "USD"

                facade.response("get-balance", balance.of(BigDecimal(1000)))
            }

            proxy.getBalance("USD").result shouldBeExactly 1000.0
        }

        it("should convert a method call into a request, and a response into a data class") {
            val reserveTokens = facade.method("reserve-tokens")
            val denomination = reserveTokens.inParameter("denomination", String::class.java)
            val amount = reserveTokens.inParameter("amount", BigDecimal::class.java)
            val timeToLive = reserveTokens.inParameter("ttl-ms", BigDecimal::class.java)

            val reservationRef = reserveTokens.outParameter("reservation-ref", UUID::class.java)
            val expirationTimestamp = reserveTokens.outParameter("expiration-timestamp", ZonedDateTime::class.java)

            val ref = UUID.randomUUID()
            val expiration = ZonedDateTime.now()

            val proxy = facade.getClientProxy<TokensFacade> { request ->
                request.facadeId shouldBe facade.facadeId
                request.methodName shouldBe reserveTokens.name
                request[denomination] shouldBe "USD"
                request[amount] shouldBe BigDecimal(1000)
                request[timeToLive] shouldBe BigDecimal(5000)

                facade.response("reserve-tokens",
                    reservationRef.of(ref),
                    expirationTimestamp.of(expiration))
            }

            proxy.reserveTokensV2("USD", BigDecimal(1000), 5000L).result shouldBe
                    TokenReservation(ref, expiration)
        }
    }

})