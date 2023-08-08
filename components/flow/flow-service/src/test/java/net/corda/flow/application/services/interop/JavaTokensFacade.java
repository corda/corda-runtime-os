package net.corda.flow.application.services.interop;

import net.corda.flow.application.services.interop.example.Denomination;
import net.corda.v5.application.interop.binding.*;
import java.math.BigDecimal;
import java.util.UUID;

@BindsFacade("org.corda.interop/platform/tokens")
@FacadeVersions({"v1.0", "v2.0"})
public interface JavaTokensFacade {

    @BindsFacadeMethod
    Double getBalance(String denomination);

    @FacadeVersions("v1.0")
    @BindsFacadeMethod("reserve-tokens")
    UUID reserveTokensV1(String denomination, BigDecimal amount);

    @FacadeVersions("v2.0")
    @BindsFacadeMethod("reserve-tokens")
    JavaTokenReservation reserveTokensV2(
            @Denomination String denomination,
            BigDecimal amount,
            @BindsFacadeParameter("ttl-ms") long timeToLiveMs);

    @BindsFacadeMethod
    void releaseReservedTokens(UUID reservationRef);

    @BindsFacadeMethod
    void spendReservedTokens(
            UUID reservationRef,
            UUID transactionRef,
            String recipient);
}
