package net.corda.interop.aliasinfo.cache

import net.corda.lifecycle.Lifecycle
import net.corda.data.interop.InteropAliasIdentity


interface AliasInfoCacheService : Lifecycle {
    fun getAliasIdentities(key: String): List<InteropAliasIdentity>
    fun putAliasIdentity(key: String, value: InteropAliasIdentity)
}
