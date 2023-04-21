package net.corda.libs.packaging

import net.corda.libs.packaging.core.CpiMetadata
import net.corda.libs.packaging.core.CpkIdentifier

interface Cpi {

    companion object {
        /**
         * Known file extensions for a CPI file; the only difference between CPI and CPB files is that CPB files
         * have no groupPolicy
         */
        val fileExtensions = listOf(".cpb", ".cpi")
    }

    val metadata : CpiMetadata
    val cpks : Collection<Cpk>

    fun getCpkById(id : CpkIdentifier) : Cpk?
}

