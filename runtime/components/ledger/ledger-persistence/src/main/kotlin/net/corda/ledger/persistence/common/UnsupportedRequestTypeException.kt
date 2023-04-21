package net.corda.ledger.persistence.common

import net.corda.data.ledger.persistence.LedgerTypes

class UnsupportedRequestTypeException(type: LedgerTypes, requestType: Class<*>) :
    IllegalArgumentException("$type request type '${requestType.name} is unsupported")