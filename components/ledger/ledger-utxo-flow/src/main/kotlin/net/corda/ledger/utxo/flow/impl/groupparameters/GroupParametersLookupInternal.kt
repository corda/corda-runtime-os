package net.corda.ledger.utxo.flow.impl.groupparameters

import net.corda.membership.lib.SignedGroupParameters
import net.corda.v5.ledger.utxo.GroupParametersLookup
import java.security.PublicKey

interface GroupParametersLookupInternal: GroupParametersLookup {
    override fun getCurrentGroupParameters(): SignedGroupParameters
    fun getMgmKeys(): List<PublicKey>
}