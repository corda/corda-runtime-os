package net.corda.v5.ledger.obsolete.contracts

import net.corda.v5.base.annotations.CordaSerializable

@CordaSerializable
data class ClassInfo(/* val cpkId: CPKId, */ val bundleName: String, val bundleVersion: String, val classname: String)
