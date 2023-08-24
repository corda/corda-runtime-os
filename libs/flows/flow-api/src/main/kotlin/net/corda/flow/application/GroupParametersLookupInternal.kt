package net.corda.flow.application

import net.corda.membership.lib.SignedGroupParameters
import net.corda.v5.membership.GroupParametersLookup
import java.security.PublicKey

interface GroupParametersLookupInternal: GroupParametersLookup {
    override fun getCurrentGroupParameters(): SignedGroupParameters
    fun getMgmKeys(): List<PublicKey>
}