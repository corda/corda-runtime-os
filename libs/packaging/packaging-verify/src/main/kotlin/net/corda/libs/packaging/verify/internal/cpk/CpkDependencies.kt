package net.corda.libs.packaging.verify.internal.cpk

import net.corda.libs.packaging.CpkDocumentReader
import net.corda.libs.packaging.core.CpkIdentifier
import net.corda.libs.packaging.signerSummaryHashForRequiredSigners
import java.io.InputStream
import java.security.CodeSigner
import java.util.NavigableSet
import java.util.TreeSet

/** Other CPKs that specific CPK depends on */
class CpkDependencies(private val jarName: String, inputStream: InputStream, cpkCodeSigners: Array<CodeSigner>) {
    private val fileLocationAppender = { msg:String -> "$msg in CPK $jarName" }
    internal var dependencies: NavigableSet<CpkIdentifier>

    init {
        dependencies = CpkDocumentReader.readDependencies(jarName, inputStream, fileLocationAppender)
        replacePlaceholders(cpkCodeSigners)
    }

    /** Replace any "same as me" placeholders with CPK's actual summary hash */
    private fun replacePlaceholders(cpkCodeSigners: Array<CodeSigner>) {
        val certificates = cpkCodeSigners.map { it.signerCertPath.certificates.first() }.toSet()
        val cpkSummaryHash = certificates.signerSummaryHashForRequiredSigners()
        dependencies = dependencies.mapTo(TreeSet()) { cpk ->
            if (cpk.signerSummaryHash === CpkDocumentReader.SAME_SIGNER_PLACEHOLDER) {
                CpkIdentifier(cpk.name, cpk.version, cpkSummaryHash)
            } else {
                cpk
            }
        }
    }
}