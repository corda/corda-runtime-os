package net.corda.cli.plugins.network.utils

import net.corda.crypto.core.ShortHash
import net.corda.v5.base.types.MemberX500Name
import net.corda.virtualnode.HoldingIdentity
import java.io.File

object HoldingIdentityUtils {
    fun getHoldingIdentity(
        holdingIdentityShortHash: ShortHash?,
        name: MemberX500Name?,
        group: String?,
    ): ShortHash {
        return holdingIdentityShortHash ?: name?.let {
            val holdingIdentity = group?.let { group ->
                HoldingIdentity(name, group)
            } ?: HoldingIdentity(name, readDefaultGroup())
            holdingIdentity.shortHash
        } ?: throw IllegalArgumentException("Either 'holdingIdentityShortHash' or 'name' must be specified.")
    }

    private fun readDefaultGroup(): String {
        val groupIdFile = File(
            File(File(File(System.getProperty("user.home")), ".corda"), "groupId"),
            "groupId.txt",
        )
        return if (groupIdFile.exists()) {
            groupIdFile.readText().trim()
        } else {
            throw IllegalArgumentException("Group ID was not specified, and the last created group could not be found.")
        }
    }
}
