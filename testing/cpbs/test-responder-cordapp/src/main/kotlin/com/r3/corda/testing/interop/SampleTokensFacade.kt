package com.r3.corda.testing.interop

import net.corda.v5.application.interop.binding.BindsFacade
import net.corda.v5.application.interop.binding.BindsFacadeMethod
import net.corda.v5.application.interop.binding.BindsFacadeParameter
import net.corda.v5.application.interop.binding.FacadeVersions
import net.corda.v5.application.interop.binding.InteropAction
import net.corda.v5.application.interop.binding.QualifiedWith
import java.math.BigDecimal
import java.time.ZonedDateTime
import java.util.*

@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.VALUE_PARAMETER)
@QualifiedWith("org.corda.interop/platform/tokens/types/denomination/1.0")
annotation class Denomination

data class TokenReservation(
    val reservationRef: UUID,
    @BindsFacadeParameter("expiration-timestamp") val expires: ZonedDateTime
)

@BindsFacade("org.corda.interop/platform/tokens")
@FacadeVersions("v2.0")
interface SampleTokensFacade {
    @BindsFacadeMethod("hello")
    fun processHello(greeting: String): @QualifiedWith("greeting") InteropAction<String>

    @FacadeVersions("v2.0")
    @BindsFacadeMethod("reserve-tokens")
    fun reserveTokensV2(
        @Denomination denomination: String,
        amount: BigDecimal,
        @BindsFacadeParameter("ttl-ms") timeToLiveMs: Long
    ): InteropAction<TokenReservation>
}
