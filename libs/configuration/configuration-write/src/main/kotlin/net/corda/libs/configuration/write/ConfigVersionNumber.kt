package net.corda.libs.configuration.write

/**
 * Records a major.minor style version.
 *
 * @property major Major version
 * @property minor Minor version
 */
class ConfigVersionNumber(val major: Int, val minor: Int) :
    Comparable<ConfigVersionNumber> {
    companion object {
        private const val BASIS = 1000000
        private val delimiters = Regex("[-.:]")

        /**
         * Parse [value] into a version number. The characters in
         * [delimiters] can be used to separate the components. This allows
         * string like `1.0-SNAPSHOT` to be parsed.
         */
        fun from(value: String): ConfigVersionNumber {
            val array = value.split(delimiters)
            return ConfigVersionNumber(array[0].toInt(), array[1].toInt())
        }
    }

    /** The absolute value of the version number, used to compare versions */
    private val absoluteValue = major * BASIS + minor

    override fun compareTo(other: ConfigVersionNumber): Int {
        return absoluteValue.compareTo(other.absoluteValue)
    }

    override fun equals(other: Any?): Boolean {
        return other is ConfigVersionNumber && absoluteValue == other.absoluteValue
    }

    override fun hashCode(): Int {
        return absoluteValue.hashCode()
    }

    override fun toString(): String {
        return "${major}.${minor}"
    }
}
