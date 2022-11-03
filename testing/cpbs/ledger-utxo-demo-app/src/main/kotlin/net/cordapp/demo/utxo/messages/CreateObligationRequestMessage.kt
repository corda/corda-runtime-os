package net.cordapp.demo.utxo.messages

import net.corda.v5.base.types.MemberX500Name
import java.math.BigDecimal

data class CreateObligationRequestMessage(
    val issuer: MemberX500Name,
    val holder: MemberX500Name,
    val amount: BigDecimal,
    val notary: MemberX500Name
)
