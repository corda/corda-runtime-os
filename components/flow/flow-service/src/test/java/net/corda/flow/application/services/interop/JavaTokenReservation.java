package net.corda.flow.application.services.interop;

import net.corda.v5.application.interop.binding.BindsFacadeParameter;
import java.time.ZonedDateTime;
import java.util.Objects;
import java.util.UUID;

public class JavaTokenReservation {

    private final UUID reservationRef;
    private final ZonedDateTime expirationTimestamp;

    public JavaTokenReservation(
        @BindsFacadeParameter("reservation-ref") UUID reservationRef,
        @BindsFacadeParameter("expiration-timestamp") ZonedDateTime expirationTimestamp) {

        this.reservationRef = reservationRef;
        this.expirationTimestamp = expirationTimestamp;
    }

    // The getter isn't annotated, but we can infer the name of the bound parameter via its association with
    // the constructor parameter.
    public UUID getReservationRef() {
        return reservationRef;
    }

    // The property name, "expiry", doesn't match up to the constructor parameter name, "expirationTimestamp",
    // but it doesn't matter because the annotations enable them to be paired together.
    @BindsFacadeParameter("expiration-timestamp")
    public ZonedDateTime getExpiry() {
        return expirationTimestamp;
    }

    // This property name doesn't correspond to a constructor parameter or an out-parameter; it should be ignored
    // by the binder.
    public boolean isExpired() {
        return expirationTimestamp.isBefore(ZonedDateTime.now());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        JavaTokenReservation that = (JavaTokenReservation) o;
        return reservationRef.equals(that.reservationRef) && expirationTimestamp.equals(that.expirationTimestamp);
    }

    @Override
    public int hashCode() {
        return Objects.hash(reservationRef, expirationTimestamp);
    }
}
