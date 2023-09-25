package com.r3.corda.demo.interop.tokens.workflows.interop

import net.corda.v5.application.crypto.DigitalSignatureAndMetadata
import net.corda.v5.application.interop.binding.BindsFacade
import net.corda.v5.application.interop.binding.BindsFacadeMethod
import net.corda.v5.application.interop.binding.BindsFacadeParameter
import net.corda.v5.application.interop.binding.FacadeVersions
import net.corda.v5.base.annotations.Suspendable
import java.math.BigDecimal
import java.util.*

@BindsFacade("org.corda.interop/platform/lock")
@FacadeVersions("v1.0")
interface LockFacade {


    @FacadeVersions("v1.0")
    @BindsFacadeMethod("create-lock")
    @Suspendable
    fun createLock(@Denomination denomination: String,
                   amount: BigDecimal,
                   @BindsFacadeParameter("notary-keys") notaryKeys: String,
                   draftTxId: String): UUID

    @FacadeVersions("v1.0")
    @BindsFacadeMethod("unlock")
    @Suspendable
    fun unlock(
        reservationRef: UUID,
        @BindsFacadeParameter("signed-tx") proof: DigitalSignatureAndMetadata,
        key: ByteArray
    ):BigDecimal

}