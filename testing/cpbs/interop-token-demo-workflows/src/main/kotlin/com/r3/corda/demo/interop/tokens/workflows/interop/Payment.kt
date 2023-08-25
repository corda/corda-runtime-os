package com.r3.corda.demo.interop.tokens.workflows.interop

import net.corda.v5.base.annotations.CordaSerializable
import java.math.BigDecimal
@CordaSerializable
data class Payment(
    val applicationName : String,
    val toReserve : BigDecimal
)