package com.r3.corda.demo.interop.delivery.workflows.interop

import net.corda.v5.base.annotations.CordaSerializable
import java.math.BigDecimal
@CordaSerializable
data class Payment(
    val interopGroupId : String = "3dfc0aae-be7c-44c2-aa4f-4d0d7145cf08",
    val toReserve : BigDecimal
)