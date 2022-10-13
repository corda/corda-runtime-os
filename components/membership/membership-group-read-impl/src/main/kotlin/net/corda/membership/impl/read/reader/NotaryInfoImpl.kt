package net.corda.membership.impl.read.reader

import net.corda.v5.base.types.MemberX500Name
import net.corda.v5.membership.NotaryInfo

internal data class NotaryInfoImpl(
    override val party: MemberX500Name,
    override val pluginClass: String,
) : NotaryInfo
