package net.corda.ledger.utxo.flow.impl.groupparameters

import net.corda.membership.lib.SignedGroupParameters
import net.corda.v5.ledger.utxo.GroupParametersService
import java.security.PublicKey

interface GroupParametersServiceInternal: GroupParametersService {
    override fun getGroupParameters(): SignedGroupParameters
    fun getMgmKeys(): List<PublicKey>
}