package net.corda.db.testkit.dbutilsimpl

fun getPropertyNonBlank(key: String, defaultValue: String): String {
    val value = System.getProperty(key)
    return if (value.isNullOrBlank()) {
        defaultValue
    } else {
        value
    }
}
