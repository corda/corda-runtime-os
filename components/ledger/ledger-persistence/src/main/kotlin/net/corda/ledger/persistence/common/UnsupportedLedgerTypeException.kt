package net.corda.ledger.persistence.common

import net.corda.data.ledger.persistence.LedgerTypes

class UnsupportedLedgerTypeException(type: LedgerTypes) : IllegalArgumentException("Unsupported ledger type '$type'")