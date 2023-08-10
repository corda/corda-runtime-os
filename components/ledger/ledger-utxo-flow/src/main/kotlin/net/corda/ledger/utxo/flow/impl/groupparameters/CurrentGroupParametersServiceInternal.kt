package net.corda.ledger.utxo.flow.impl.groupparameters

import net.corda.membership.lib.SignedGroupParameters
import net.corda.v5.ledger.utxo.CurrentGroupParametersService
import java.security.PublicKey

interface CurrentGroupParametersServiceInternal: CurrentGroupParametersService {
    override fun getCurrentGroupParameters(): SignedGroupParameters
    fun getMgmKeys(): List<PublicKey>
}