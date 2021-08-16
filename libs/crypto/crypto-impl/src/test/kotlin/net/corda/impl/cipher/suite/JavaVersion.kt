package net.corda.impl.cipher.suite

enum class JavaVersion(val versionString: String) {
    Java_1_8("1.8"),
    Java_11("11");

    companion object {
        fun isVersionAtLeast(version: JavaVersion): Boolean {
            return currentVersion.toFloat() >= version.versionString.toFloat()
        }

        private val currentVersion: String = System.getProperty("java.specification.version") ?:
                                               throw IllegalStateException("Unable to retrieve system property java.specification.version")
    }
}