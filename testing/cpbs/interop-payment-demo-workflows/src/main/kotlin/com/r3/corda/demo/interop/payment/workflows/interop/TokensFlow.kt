package com.r3.corda.demo.interop.payment.workflows.interop

import net.corda.v5.application.flows.CordaInject
import net.corda.v5.application.interop.binding.InteropAction
import net.corda.v5.application.membership.MemberLookup
import net.corda.v5.base.types.MemberX500Name
import java.math.BigDecimal
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit
import java.util.*

data class Reservation(val ref: UUID, val denomination: String, val amount: Double, val expires: ZonedDateTime)
data class Spend(val reservation: Reservation, val transactionRef: UUID, val recipient: String)


class TokensFlow(initialBalances: Map<String, BigDecimal>, private val timeserver: () -> ZonedDateTime) :
     FacadeDispatcherFlow(), TokensFacade {

    @CordaInject
    lateinit var memberLookup: MemberLookup

    private var balances = initialBalances.toMutableMap()
    private val reservations = mutableMapOf<UUID, Reservation>()
    val spendHistory = mutableListOf<Spend>()

    override fun getBalance(denomination: String): InteropAction<Double> {
        val totalBalance = balances[denomination] ?: BigDecimal(0)
        var now = timeserver()

        val reserved = reservations.values.filter {
            it.denomination == denomination && it.expires.isAfter(now)
        }.sumOf { it.amount }

        return InteropAction.ServerResponse(totalBalance.toDouble() - reserved)
    }

    override fun reserveTokensV1(denomination: String, amount: BigDecimal): InteropAction<UUID> {
        return InteropAction.ServerResponse(reserveTokensV2(denomination, amount, 24 * 60 * 1000)
            .result.reservationRef)
    }

    override fun reserveTokensV2(
        denomination: String,
        amount: BigDecimal,
        timeToLiveMs: Long
    ): InteropAction<TokenReservation> {
        val ref = UUID.randomUUID()
        val expirationTimestamp = timeserver().plus(timeToLiveMs, ChronoUnit.MILLIS)

        reservations[ref] = Reservation(ref, denomination, amount.toDouble(), expirationTimestamp)

        return InteropAction.ServerResponse(TokenReservation(ref, expirationTimestamp))
    }

    override fun releaseReservedTokens(reservationRef: UUID): InteropAction<Unit> {
        reservations.remove(reservationRef)

        return InteropAction.ServerResponse(Unit)
    }

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