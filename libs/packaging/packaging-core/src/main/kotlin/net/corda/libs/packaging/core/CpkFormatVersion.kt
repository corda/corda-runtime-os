package net.corda.libs.packaging.core

import net.corda.data.packaging.CpkFormatVersion as CpkFormatVersionAvro

data class CpkFormatVersion(val major: Int, val minor: Int) {
    companion object {
        fun fromAvro(other: CpkFormatVersionAvro) : CpkFormatVersion =
            CpkFormatVersion(other.major, other.minor)

        fun fromString(other: String): CpkFormatVersion {
            val (major, minor) = other.split(".")
            return CpkFormatVersion(major.toInt(), minor.toInt())
        }
    }
    fun toAvro(): CpkFormatVersionAvro = CpkFormatVersionAvro(major, minor)
    override fun toString() = "$major.$minor"
}