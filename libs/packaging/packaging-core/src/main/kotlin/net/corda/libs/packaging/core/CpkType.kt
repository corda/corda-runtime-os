package net.corda.libs.packaging.core

import net.corda.data.packaging.CpkType as CpkTypeAvro

/**
 * [CpkType] is used to distinguish between different types of Cpk,
 * its main purpose is currently to distinguish between Cpks that are part
 * of the Corda runtime itself (and don't need to be resolved during dependency resolution)
 * and those that are not
 */
enum class CpkType(private val text: String?) : Comparable<CpkType> {
    CORDA_API("corda-api"), UNKNOWN(null), SYNTHETIC(null);

    companion object{
        private val map = buildMap {
            for (cpkType in values()) {
                cpkType.text?.also { text ->
                    this[text] = cpkType
                }
            }
        }

        /**
         * Parses [CpkType] from a [String], if the [String] cannot be parsed this function returns [CpkType.UNKNOWN]
         * @param text the [String] to be parsed
         * @return a [CpkType] instance
         */
        @JvmStatic
        fun parse(text : String) = map[text.lowercase()] ?: UNKNOWN

        fun fromAvro(other: CpkTypeAvro) : CpkType = when (other) {
            CpkTypeAvro.UNKNOWN -> UNKNOWN
            CpkTypeAvro.CORDA_API -> CORDA_API
        }
    }

    fun toAvro(): CpkTypeAvro = when (this) {
        UNKNOWN -> CpkTypeAvro.UNKNOWN
        CORDA_API -> CpkTypeAvro.CORDA_API
        SYNTHETIC -> CpkTypeAvro.UNKNOWN
    }
}