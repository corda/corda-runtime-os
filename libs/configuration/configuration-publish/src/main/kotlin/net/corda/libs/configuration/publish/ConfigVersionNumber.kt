package net.corda.libs.configuration.publish

import javax.naming.ConfigurationException

/**
 * Records a major.minor style version.
 *
 * @property major Major version
 * @property minor Minor version
 */
@Suppress("Deprecation")
@Deprecated("Deprecated in line with the deprecation of `ConfigWriter`.")
data class ConfigVersionNumber(val major: Int, val minor: Int) {
    companion object {
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
}
