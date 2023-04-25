package com.r3.corda.testing.testflows.messages

import java.math.BigDecimal

data class TokenSelectionResponse (
    val resultType: String,
    val tokenAmounts: List<BigDecimal>
)
