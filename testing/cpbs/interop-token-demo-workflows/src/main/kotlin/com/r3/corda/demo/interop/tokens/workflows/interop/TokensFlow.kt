package com.r3.corda.demo.interop.tokens.workflows.interop

import com.r3.corda.demo.interop.tokens.workflows.IssueFlowArgs
import com.r3.corda.demo.interop.tokens.workflows.IssueFlowResult
import com.r3.corda.demo.interop.tokens.workflows.IssueSubFlow
import com.r3.corda.demo.interop.tokens.workflows.TransferFlowArgs
import com.r3.corda.demo.interop.tokens.workflows.TransferFlowResult
import com.r3.corda.demo.interop.tokens.workflows.TransferSubFlow
import net.corda.v5.application.flows.CordaInject
import net.corda.v5.application.flows.FlowEngine
import net.corda.v5.application.interop.binding.InteropAction
import net.corda.v5.application.membership.MemberLookup
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.base.types.MemberX500Name
import org.slf4j.LoggerFactory
import java.math.BigDecimal
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit
import java.util.*

data class Reservation(val ref: UUID, val denomination: String, val amount: Double, val expires: ZonedDateTime)
data class Spend(val reservation: Reservation, val transactionRef: UUID, val recipient: String)


class TokensFlow: FacadeDispatcherFlow(), TokensFacade {

    private companion object {
        val log = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    @CordaInject
    lateinit var memberLookup: MemberLookup

    @CordaInject
    lateinit var flowEngine: FlowEngine

    private val timeserver : () -> ZonedDateTime = { ZonedDateTime.now() }
    private var balances = mutableMapOf("USD" to BigDecimal(100), "EUR" to BigDecimal(100))
    private val reservations = mutableMapOf<UUID, Reservation>()
    val spendHistory = mutableListOf<Spend>()

    @Suspendable
    override fun getBalance(denomination: String): InteropAction<Double> {
        val totalBalance = balances[denomination] ?: BigDecimal(0)
        var now = timeserver()

        val reserved = reservations.values.filter {
            it.denomination == denomination && it.expires.isAfter(now)
        }.sumOf { it.amount }

        return InteropAction.ServerResponse(totalBalance.toDouble() - reserved)
    }

    @Suspendable
    override fun reserveTokensV1(denomination: String, amount: BigDecimal): InteropAction<UUID> {
        log.info("reserveTokensV1 $denomination $amount invokes on ${memberLookup.myInfo().name}")
        return InteropAction.ServerResponse(reserveTokensV2(denomination, amount, 24 * 60 * 1000)
            .result.reservationRef)
    }

    @Suspendable
    override fun reserveTokensV2(
        denomination: String,
        amount: BigDecimal,
        timeToLiveMs: Long
    ): InteropAction<TokenReservation> {
        log.info("reserveTokensV2 $denomination $amount")
        val ref = UUID.randomUUID()
        val expirationTimestamp = timeserver().plus(timeToLiveMs, ChronoUnit.MILLIS)

        reservations[ref] = Reservation(ref, denomination, amount.toDouble(), expirationTimestamp)

        return InteropAction.ServerResponse(TokenReservation(ref, expirationTimestamp))
    }

    @Suspendable
    override fun reserveTokensV3(
        denomination: String,
        amount: BigDecimal,
        timeToLiveMs: Long
    ): InteropAction<SimpleTokenReservation> {
        log.info("reserveTokensV3 $denomination $amount")
        val ref = UUID.randomUUID()
        val expirationTimestamp = timeserver().plus(timeToLiveMs, ChronoUnit.MILLIS)

        reservations[ref] = Reservation(ref, denomination, amount.toDouble(), expirationTimestamp)

        val result : IssueFlowResult = flowEngine.subFlow(IssueSubFlow(IssueFlowArgs(amount.toString())))

        return InteropAction.ServerResponse(SimpleTokenReservation(ref, result.stateId))
    }

    @Suspendable
    override fun releaseReservedTokens(reservationRef: UUID): InteropAction<Unit> {
        reservations.remove(reservationRef)

        return InteropAction.ServerResponse(Unit)
    }

    @Suspendable
    override fun spendReservedTokens(
        reservationRef: UUID,
        transactionRef: UUID,
        recipient: String
    ): InteropAction<Unit> {
        val reservation = reservations[reservationRef] ?:
        throw IllegalArgumentException("Reservation $reservationRef does not exist")

        if (reservation.expires.isBefore(timeserver())) throw IllegalStateException("Reservation has expired")

        reservations.remove(reservationRef)
        balances.compute(reservation.denomination) { _, balance ->
            if (balance == null) throw IllegalStateException("No balance exists for ${reservation.denomination}")
            balance.subtract(BigDecimal(reservation.amount))
        }

        memberLookup.lookup(MemberX500Name.parse(recipient))
        spendHistory.add(Spend(reservation, transactionRef, recipient))


         flowEngine.subFlow(TransferSubFlow(TransferFlowArgs(recipient, reservationRef)))

        return InteropAction.ServerResponse(Unit)
    }

}