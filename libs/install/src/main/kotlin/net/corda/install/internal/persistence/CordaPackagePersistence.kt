package net.corda.install.internal.persistence

import net.corda.packaging.Cpb
import net.corda.packaging.Cpk
import net.corda.v5.crypto.SecureHash
import java.io.InputStream

/** Provides persistence for CPBs and CPKs. */
internal interface CordaPackagePersistence {
    /** Retrieves the set of [Cpk]s identified by [cpbIdentifier], or null if the identifier is unknown. */
    fun get(cpbIdentifier: Cpb.Identifier): Cpb.Expanded?
    /** Retrieves the [Cpk] identified by [id], or null if the hash is unknown. */
    fun getCpk(id : Cpk.Identifier) : Cpk.Expanded?
    /** Retrieves the [Cpk] identified by [cpkHash], or null if the hash is unknown. */
    fun get(cpkHash: SecureHash): Cpk.Expanded?
    /** Persists a [Cpb] (it will not survive a node restart) */
    fun putCpb(inputStream : InputStream) : Cpb.Expanded
    /** Persists a [Cpk] (it will survive node restarts). */
    fun putCpk(inputStream : InputStream) : Cpk.Expanded
    /** The list of CPBs installed in this module */
    fun getCpbIdentifiers(): Set<Cpb.Identifier>
    /** Returns true if the CPK is installed */
    fun hasCpk(cpkHash: SecureHash): Boolean
}