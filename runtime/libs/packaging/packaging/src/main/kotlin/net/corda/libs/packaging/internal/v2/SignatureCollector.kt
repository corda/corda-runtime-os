package net.corda.libs.packaging.internal.v2

import java.util.jar.JarEntry

internal class SignatureCollector {

    companion object {
        /**
         * @see <https://docs.oracle.com/javase/8/docs/technotes/guides/jar/jar.html#Signed_JAR_File>
         * Additionally accepting *.EC as its valid for [java.util.jar.JarVerifier] and jarsigner @see https://docs.oracle.com/javase/8/docs/technotes/tools/windows/jarsigner.html,
         * temporally treating META-INF/INDEX.LIST as unsignable entry because [java.util.jar.JarVerifier] doesn't load its signers.
         */
        private val unsignableEntryName = "META-INF/(?:(?:.*[.](?:SF|DSA|RSA|EC)|SIG-.*)|INDEX\\.LIST)".toRegex()

        /**
         * @return if the [entry] [JarEntry] can be signed.
         */
        @JvmStatic
        fun isSignable(entry: JarEntry): Boolean = !entry.isDirectory && !unsignableEntryName.matches(entry.name)
    }
}
