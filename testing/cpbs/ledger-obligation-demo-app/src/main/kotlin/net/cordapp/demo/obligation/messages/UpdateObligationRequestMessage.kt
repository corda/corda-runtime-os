package net.cordapp.demo.obligation.messages

import java.math.BigDecimal
import java.util.UUID

data class UpdateObligationRequestMessage(val id: UUID, val amountToSettle: BigDecimal)
