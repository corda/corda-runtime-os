package net.corda.ledger.notary

import net.corda.v5.base.types.MemberX500Name
import net.corda.v5.ledger.common.NotaryInfo
import java.security.PublicKey

data class NotaryInfoImpl(
    override val name: MemberX500Name,
    override val pluginClass: String,
    override val publicKey: PublicKey,
) : NotaryInfo