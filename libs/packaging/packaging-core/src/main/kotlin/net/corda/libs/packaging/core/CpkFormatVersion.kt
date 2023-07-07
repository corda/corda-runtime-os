package net.corda.libs.packaging.core

import net.corda.data.packaging.CpkFormatVersion as CpkFormatVersionAvro

data class CpkFormatVersion(val major: Int, val minor: Int) {
    companion object {
        fun fromAvro(other: CpkFormatVersionAvro) : CpkFormatVersion =
            CpkFormatVersion(other.major, other.minor)

        // N.B.: The following function assumes the string follows the
        // format "major.minor" if the pre-condition is not respected
        // then the behaviour is undefined
        fun fromString(str: String): CpkFormatVersion {
            val tokens = str.split(".");

            val major = tokens[0].toInt()
            val minor = tokens[1].toInt()

            return CpkFormatVersion(major, minor)
        }
    }
    fun toAvro(): CpkFormatVersionAvro = CpkFormatVersionAvro(major, minor)
    override fun toString() = "$major.$minor"
}