package net.corda.interop.service

import net.corda.v5.application.interop.InteropGroupInfo

interface AliasIdentityDataService {
    fun getAliasIdentityData() : List<InteropGroupInfo>
}