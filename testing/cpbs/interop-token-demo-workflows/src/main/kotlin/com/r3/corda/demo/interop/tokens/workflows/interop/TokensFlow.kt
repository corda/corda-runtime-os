package com.r3.corda.demo.interop.tokens.workflows.interop

import net.corda.v5.application.flows.CordaInject
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


class TokensFlow(initialBalances: Map<String, BigDecimal>, private val timeserver: () -> ZonedDateTime) :
     FacadeDispatcherFlow(), TokensFacade {

    private companion object {
        val log = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    @CordaInject
    lateinit var memberLookup: MemberLookup

    private var balances = initialBalances.toMutableMap()
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
        log.info("reserveTokensV1 $denomination $amount")
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

        return InteropAction.ServerResponse(Unit)
    }

}