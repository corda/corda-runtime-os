package net.corda.v5.ledger.obsolete.contracts

import net.corda.v5.base.annotations.CordaSerializable
import net.corda.v5.crypto.SecureHash

typealias CPKId = SecureHash

@CordaSerializable
data class PackageIdWithDependencies(val packageId: CPKId, val dependencyIds: List<CPKId>)
