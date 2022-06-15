package net.corda.libs.packaging.verify.internal.cpk

import net.corda.libs.packaging.CpkDocumentReader
import net.corda.libs.packaging.PackagingConstants
import net.corda.libs.packaging.core.exception.LibraryIntegrityException
import net.corda.libs.packaging.core.exception.PackagingException
import java.io.InputStream

class CpkLibraryConstraints(private val jarName: String, inputStream: InputStream) {
    private val fileLocationAppender = { msg: String -> "$msg in CPK $jarName" }
    private val libraryMap = CpkDocumentReader.readLibraryConstraints(jarName, inputStream, fileLocationAppender)

    fun verify(libraries: Collection<CpkLibrary>) {
        libraries.forEach {
            val requiredHash = libraryMap[it.name] ?: throw PackagingException(
                "${it.name} not found in ${PackagingConstants.CPK_DEPENDENCY_CONSTRAINTS_FILE_ENTRY}")
            if (it.hash != requiredHash) {
                throw LibraryIntegrityException(
                    "Hash of '${it.name}' differs from the content of ${PackagingConstants.CPK_DEPENDENCY_CONSTRAINTS_FILE_ENTRY}"
                )
            }
        }
    }

}