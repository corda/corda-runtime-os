package net.corda.interop.write.service

import net.corda.interop.write.service.data.AliasIdentity
import net.corda.lifecycle.Lifecycle


interface InteropWriteService : Lifecycle {
    fun publishAliasIdentity(aliasIdentity: AliasIdentity)

    fun publishHostedAliasIdentity(aliasIdentity: AliasIdentity)
}