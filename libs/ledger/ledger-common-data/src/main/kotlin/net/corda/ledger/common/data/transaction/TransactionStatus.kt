package net.corda.ledger.common.data.transaction

import java.security.InvalidParameterException

enum class TransactionStatus(val stringValue: String) {
    INVALID("I"),
    UNVERIFIED("U"),
    VERIFIED("V");

    companion object {
        fun String.toTransactionStatus() = when {
            this.equals(INVALID.stringValue, ignoreCase = true) -> INVALID
            this.equals(UNVERIFIED.stringValue, ignoreCase = true) -> UNVERIFIED
            this.equals(VERIFIED.stringValue, ignoreCase = true) -> VERIFIED
            else -> throw InvalidParameterException("TransactionStatus $this is not supported")
        }
    }
}