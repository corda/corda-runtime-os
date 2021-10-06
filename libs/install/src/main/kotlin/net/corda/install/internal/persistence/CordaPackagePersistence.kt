package net.corda.install.internal.persistence

import net.corda.packaging.CPI
import net.corda.packaging.CPK
import net.corda.v5.crypto.SecureHash
import java.io.InputStream

/** Provides persistence for CPBs and CPKs. */
internal interface CordaPackagePersistence {
    /** Retrieves the set of [Cpk]s identified by [cpbIdentifier], or null if the identifier is unknown. */
    fun get(cpbIdentifier: CPI.Identifier): CPI?
    /** Retrieves the [CPK] identified by [id], or null if the hash is unknown. */
    fun getCpk(id : CPK.Identifier) : CPK?
    /** Retrieves the [CPK] identified by [cpkHash], or null if the hash is unknown. */
    fun get(cpkHash: SecureHash): CPK?
    /** Persists a [CPI] (it will not survive a node restart) */
    fun putCpb(inputStream : InputStream) : CPI
    /** Persists a [CPK] (it will survive node restarts). */
    fun putCpk(inputStream : InputStream) : CPK
    /** The list of CPBs installed in this module */
    fun getCpbIdentifiers(): Set<CPI.Identifier>
    /** Returns true if the CPK is installed */
    fun hasCpk(cpkHash: SecureHash): Boolean
}