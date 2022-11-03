package net.cordapp.demo.utxo.messages

import java.math.BigDecimal
import java.util.*

data class UpdateObligationRequestMessage(val id: UUID, val amountToSettle: BigDecimal)
