package net.corda.interop.service

import net.corda.v5.interop.InteropGroupInfo

interface AliasIdentityDataService {
    fun getAliasIdentityData() : List<InteropGroupInfo>
}