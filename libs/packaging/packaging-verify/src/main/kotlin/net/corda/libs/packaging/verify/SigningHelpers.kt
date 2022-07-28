package net.corda.libs.packaging.verify

import java.util.jar.JarEntry

object SigningHelpers {

    /** Check whether JAR entry is signing related */
    fun isSigningRelated(jarEntry: JarEntry): Boolean {
        val name = jarEntry.name
        var uppercaseName = name.uppercase()
        if (!uppercaseName.startsWith("META-INF/"))
            return false

        // Discard "META-INF/" prefix
        uppercaseName = uppercaseName.substring(9)
        if (uppercaseName.indexOf('/') != -1)
            return false

        if (isBlockOrSF(uppercaseName) || (uppercaseName == "MANIFEST.MF"))
            return true

        if (uppercaseName.startsWith("SIG-")) {
            extension(uppercaseName)?.let {
                if (!extensionValid(it))
                    return false
            }
            return true // no extension is OK
        }
        return false
    }

    /** Returns file's extension */
    private fun extension(fileName: String): String? {
        val extIndex = fileName.lastIndexOf('.')
        return if (extIndex != -1) {
            fileName.substring(extIndex + 1)
        } else {
            null
        }
    }

    /**
     * Checks whether file extension is valid.
     * See http://docs.oracle.com/javase/7/docs/technotes/guides/jar/jar.html#Digital_Signatures
     * */
    private fun extensionValid(extension: String): Boolean =
        extension.isNotEmpty() && extension.length > 3 && extension.all { it.isLetterOrDigit() }

    /** Checks whether file is a signing file or signing block file */
    private fun isBlockOrSF(s: String): Boolean {
        return (s.endsWith(".SF")
                || s.endsWith(".DSA")
                || s.endsWith(".RSA")
                || s.endsWith(".EC"))
    }
}