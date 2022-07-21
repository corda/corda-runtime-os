package net.corda.libs.packaging.internal.v1

import java.security.CodeSigner
import java.security.cert.Certificate
import java.util.Arrays
import java.util.Collections
import java.util.jar.JarEntry

/**
 * Helper class to extract signatures from a jar file, it has to be used calling [addEntry] on all of the jar's [JarEntry]
 * after having consumed their entry content from the source [java.util.jar.JarInputStream], then [certificates] property
 * will contain the public keys of the jar's signers.
 */
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

        private fun signers2OrderedPublicKeys(signers : Array<CodeSigner>) =
                signers.mapTo(LinkedHashSet()) { (it.signerCertPath.certificates[0]).publicKey }
    }

    private var firstSignedEntry : String? = null
    private var codeSigners : Array<CodeSigner>? = null
    private val _certificates = mutableSetOf<Certificate>()
    val certificates : Set<Certificate>
        get() = Collections.unmodifiableSet(_certificates)

    fun addEntry(jarEntry : JarEntry) {
        if(isSignable(jarEntry)) {
            val entrySigners = jarEntry.codeSigners ?: emptyArray()
            if (codeSigners == null) {
                codeSigners = entrySigners
                firstSignedEntry = jarEntry.name
                for (signer in entrySigners) {
                    _certificates.add(signer.signerCertPath.certificates.first())
                }
            }
            if (!Arrays.equals(codeSigners, entrySigners)) {
                throw throw IllegalArgumentException(
                    """
                    Mismatch between signers ${signers2OrderedPublicKeys(codeSigners!!)} for file $firstSignedEntry
                    and signers ${signers2OrderedPublicKeys(entrySigners)} for file ${jarEntry.name}
                    """.trimIndent().replace('\n', ' '))
            }
        }
    }
}