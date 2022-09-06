@file:JvmName("LedgerGroupParameters")

package net.corda.v5.ledger.obsolete

import net.corda.v5.membership.GroupParameters

private const val NOTARIES_KEY = "corda.notaries"

/**
 * A list of all available notaries in the group.
 * This attempts to parse the available notary information from the group parameters internal property map.
 *
 * Note: This is due to be reimplemented which is why it is currently in the `obsolete` module.
 */
val GroupParameters.notaries: List<NotaryInfo>
    get() = parseList(
        NOTARIES_KEY,
        NotaryInfo::class.java
    )
