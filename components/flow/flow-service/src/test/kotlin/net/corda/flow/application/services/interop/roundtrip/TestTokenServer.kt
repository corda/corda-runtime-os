package net.corda.flow.application.services.interop.roundtrip

import net.corda.flow.application.services.interop.example.TokenReservation
import net.corda.flow.application.services.interop.example.TokensFacade
import java.math.BigDecimal
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit
import java.util.*

data class Reservation(val ref: UUID, val denomination: String, val amount: Double, val expires: ZonedDateTime)
data class Spend(val reservation: Reservation, val transactionRef: UUID, val recipient: String)

/**
 * A toy token server. Not secure, thread-safe, efficient or in any other respect fit for production use.
 */
class TestTokenServer(initialBalances: Map<String, BigDecimal>, private val timeserver: () -> ZonedDateTime) :
    TokensFacade {

    private var balances = initialBalances.toMutableMap()
    private val reservations = mutableMapOf<UUID, Reservation>()
    val spendHistory = mutableListOf<Spend>()

    override fun getBalance(denomination: String): Double {
        val totalBalance = balances[denomination] ?: BigDecimal(0)
        val now = timeserver()

        val reserved = reservations.values.filter {
            it.denomination == denomination && it.expires.isAfter(now)
        }.sumOf { it.amount }

        return totalBalance.toDouble() - reserved
    }

    override fun reserveTokensV1(denomination: String, amount: BigDecimal): UUID {
        return reserveTokensV2(denomination, amount, 24 * 60 * 1000)
            .reservationRef
    }

    override fun reserveTokensV2(
        denomination: String,
        amount: BigDecimal,
        timeToLiveMs: Long
    ): TokenReservation {
        val ref = UUID.randomUUID()
        val expirationTimestamp = timeserver().plus(timeToLiveMs, ChronoUnit.MILLIS)

        reservations[ref] = Reservation(ref, denomination, amount.toDouble(), expirationTimestamp)

        return TokenReservation(ref, expirationTimestamp)
    }

    override fun releaseReservedTokens(reservationRef: UUID) {
        reservations.remove(reservationRef)
    }

    override fun spendReservedTokens(
        reservationRef: UUID,
        transactionRef: UUID,
        recipient: String
    ) {
        val reservation = reservations[reservationRef] ?:
        throw IllegalArgumentException("Reservation $reservationRef does not exist")

        if (reservation.expires.isBefore(timeserver())) throw IllegalStateException("Reservation has expired")

        reservations.remove(reservationRef)
        balances.compute(reservation.denomination) { _, balance ->
            if (balance == null) throw IllegalStateException("No balance exists for ${reservation.denomination}")
            balance.subtract(BigDecimal(reservation.amount))
        }
        spendHistory.add(Spend(reservation, transactionRef, recipient))
    }

}