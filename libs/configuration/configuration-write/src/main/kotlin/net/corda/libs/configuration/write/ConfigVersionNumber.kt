package net.corda.libs.configuration.write

import javax.naming.ConfigurationException

/**
 * Records a major.minor style version.
 *
 * @property major Major version
 * @property minor Minor version
 */
@Suppress("Deprecation")
@Deprecated("Deprecated in line with the deprecation of `ConfigWriter`.")
class ConfigVersionNumber(val major: Int, val minor: Int) :
    Comparable<ConfigVersionNumber> {
    companion object {
        private const val BASIS = 1000000
        private val delimiters = Regex("[-.:]")

        /**
         * Parse [value] into a version number. The characters in
         * [delimiters] can be used to separate the components. This allows
         * string like `1.0-SNAPSHOT` to be parsed.
         *
         * Throws [ConfigurationException] if the given version does not pass validation
         */
        fun from(value: String): ConfigVersionNumber {
            val regex = """^(\d+)\.(\d+)(-SNAPSHOT|-snapshot)?$""".toRegex()
            if (regex.matches(value)) {
                val array = value.split(delimiters)
                return ConfigVersionNumber(array[0].toInt(), array[1].toInt())
            } else {
                throw ConfigurationException("Parsing exception for provided version. Given version $value does not " +
                        "match major.minor(-SNAPSHOT)")
            }

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
