package net.corda.ledger.utxo.flow.impl.groupparameters

import net.corda.membership.lib.SignedGroupParameters
import java.security.PublicKey

interface CurrentGroupParametersService {

    fun get(): SignedGroupParameters

    fun getMgmKeys(): List<PublicKey>
}