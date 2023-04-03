package net.corda.ledger.common.data.transaction

class InvalidTransactionMetadataException(cause: Throwable) : Exception("Invalid or missing transaction metadata: ${cause.message}", cause)