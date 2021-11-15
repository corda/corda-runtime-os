package net.corda.install.internal.verification

import net.corda.packaging.CPK

/** Performs some check on a group of CPKs. */
internal interface CpkVerifier {
    /**
    * Verifies whether a given group of [cpks] satisfy a given set of constraints.
    */
    fun verify(cpks: Iterable<CPK.Metadata>)
}

/**
 * Performs verification of a CPK in isolation (e.g. that the CPK is well-formed).
 *
 * These checks can always be performed at install time.
 */
internal interface StandaloneCpkVerifier: CpkVerifier

/**
 * Performs some check of a group of CPKs taken together (e.g. that there are no conflicts ebtween the CPKs).
 *
 * These checks can be performed at install time for CPBs, for which the grouping of CPKs is pre-defined, but not
 * for user-defined groupings of CPKs.
 */
internal interface GroupCpkVerifier: CpkVerifier