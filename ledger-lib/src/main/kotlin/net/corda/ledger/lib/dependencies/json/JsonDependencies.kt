package net.corda.ledger.lib.dependencies.json

import net.corda.application.impl.services.json.JsonMarshallingServiceImpl
import net.corda.common.json.validation.impl.JsonValidatorImpl
import net.corda.ledger.lib.dependencies.crypto.CryptoDependencies.merkleProofProvider

object JsonDependencies {
    // ------------------------------------------------------------------------
    // ALL GOOD
    val jsonValidator = JsonValidatorImpl()
    val jsonMarshallingService = JsonMarshallingServiceImpl(merkleProofProvider)
    // ------------------------------------------------------------------------
}