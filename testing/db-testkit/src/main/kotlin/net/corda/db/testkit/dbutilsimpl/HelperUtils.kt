package net.corda.db.testkit.dbutilsimpl


object DbUtilsHelperFunctions {
    /**
     * This function is very similar to System.getProperty(key, defaultValue), but it also
     * applies the default value to blank properties.
     */
    fun getPropertyNonBlank(key: String, defaultValue: String): String {
        val value = System.getProperty(key)
        return if (value.isNullOrBlank()) {
            defaultValue
        } else {
            value
        }
    }
}
