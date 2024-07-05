package net.corda.ledger.lib.impl.stub.groupparameters

import net.corda.flow.application.GroupParametersLookupInternal
import net.corda.membership.lib.SignedGroupParameters
import java.security.PublicKey

class StubGroupParametersLookup : GroupParametersLookupInternal {
    override fun getCurrentGroupParameters(): SignedGroupParameters {
        return StubSignedGroupParameters()
    }

    // No need to implement
    override fun getMgmKeys(): List<PublicKey> {
        TODO("Not yet implemented")
    }
}