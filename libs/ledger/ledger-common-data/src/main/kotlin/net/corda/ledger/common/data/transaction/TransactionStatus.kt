package net.corda.ledger.common.data.transaction

import java.security.InvalidParameterException

enum class TransactionStatus(val value: String) {
    INVALID("I"),
    UNVERIFIED("U"),
    VERIFIED("V"),
    DRAFT("D");

    companion object {
        fun String.toTransactionStatus() = when {
            this.equals(INVALID.value, ignoreCase = true) -> INVALID
            this.equals(UNVERIFIED.value, ignoreCase = true) -> UNVERIFIED
            this.equals(VERIFIED.value, ignoreCase = true) -> VERIFIED
            this.equals(DRAFT.value, ignoreCase = true) -> DRAFT
            else -> throw InvalidParameterException("TransactionStatus $this is not supported")
        }
    }
}