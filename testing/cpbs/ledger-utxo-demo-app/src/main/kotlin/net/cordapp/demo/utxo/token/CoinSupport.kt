package net.cordapp.demo.utxo.token

import net.corda.v5.base.types.MemberX500Name
import net.corda.v5.crypto.DigestAlgorithmName
import net.corda.v5.crypto.SecureHash
import net.corda.v5.ledger.utxo.Command
import net.corda.v5.ledger.utxo.token.selection.ClaimedToken

data class CreateCoinMessage(
    val issuerBankX500: String,
    val currency: String,
    val numberOfCoins: Int,
    val valueOfCoin: Int,
    val tag: String?,
    val ownerHash: String?
)

data class SpendCoinMessage(
    val issuerBankX500: String,
    val currency: String,
    val maxCoinsToUse: Int,
    val targetAmount: Int,
    val tag: String?,
    val ownerHash: String?,
)

data class SpendCoinResponseMessage(
    val foundCoins: List<ClaimedToken>,
    val spentCoins: List<String>,
    val releasedCoins: List<String>,
)

class NullCoinCommand : Command

fun MemberX500Name.toSecureHash(): SecureHash {
    return SecureHash(
        DigestAlgorithmName.SHA2_256.name,
        this.toString().toByteArray()
    )
}

