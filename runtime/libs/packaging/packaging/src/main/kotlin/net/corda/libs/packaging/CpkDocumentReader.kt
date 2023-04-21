package net.corda.libs.packaging

import net.corda.crypto.core.SecureHashImpl
import net.corda.libs.packaging.PackagingConstants.CPK_DEPENDENCY_CONSTRAINTS_FILE_ENTRY
import net.corda.libs.packaging.PackagingConstants.CPK_LIB_FOLDER
import net.corda.libs.packaging.core.CpkIdentifier
import net.corda.libs.packaging.core.CpkType
import net.corda.libs.packaging.core.exception.DependencyMetadataException
import net.corda.libs.packaging.core.exception.LibraryMetadataException
import net.corda.v5.crypto.DigestAlgorithmName
import net.corda.v5.crypto.SecureHash
import org.slf4j.LoggerFactory
import org.w3c.dom.Document
import org.w3c.dom.Element
import org.w3c.dom.NodeList
import org.xml.sax.ErrorHandler
import org.xml.sax.SAXNotRecognizedException
import org.xml.sax.SAXParseException
import java.io.InputStream
import java.util.Base64
import java.util.Collections
import java.util.NavigableMap
import java.util.NavigableSet
import java.util.TreeMap
import java.util.TreeSet
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.validation.SchemaFactory

class CpkDocumentReader {
    companion object {
        private val logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
        val SAME_SIGNER_PLACEHOLDER = SecureHashImpl("NONE", byteArrayOf(0))

        private const val CORDA_CPK_V1 = "corda-cpk-1.0.xsd"

        // The tags used in the XML of the file specifying a CPK's dependencies.
        private const val DEPENDENCY_TAG = "cpkDependency"
        private const val DEPENDENCY_NAME_TAG = "name"
        private const val DEPENDENCY_VERSION_TAG = "version"
        private const val DEPENDENCY_SIGNERS_TAG = "signers"
        private const val DEPENDENCY_TYPE_TAG = "type"
        private const val DEPENDENCY_SIGNER_TAG = "signer"
        private const val DEPENDENCY_SAME_SIGNER_TAG = "sameAsMe"

        // The tags used in the XML of the file specifying library constraints.
        private const val LIBRARY_CONSTRAINT_TAG = "dependencyConstraint"
        private const val LIBRARY_FILENAME_TAG = "fileName"
        private const val LIBRARY_HASH_TAG = "hash"
        private const val LIBRARY_HASH_ALGORITHM_ATTRIBUTE = "algorithm"

        /** Returns the [CpkIdentifier]s for the provided [dependenciesFileContent]. */
        @Suppress("ThrowsCount", "TooGenericExceptionCaught", "ComplexMethod")
        fun readDependencies(
            jarName: String,
            dependenciesFileContent: InputStream,
            fileLocationAppender: (String) -> String
        ): NavigableSet<CpkIdentifier> {
            val cpkDependencyDocument: Document = try {
                // The CPK dependencies are specified as an XML file.
                val documentBuilder = cpkV1DocumentBuilderFactory.newDocumentBuilder()
                documentBuilder.setErrorHandler(XmlErrorHandler(jarName))
                documentBuilder.parse(dependenciesFileContent)
            } catch (e: DependencyMetadataException) {
                throw e
            } catch (e: SecurityException) {
                throw e
            } catch (e: Exception) {
                throw DependencyMetadataException(fileLocationAppender("Error parsing CPK dependencies file"), e)
            }
            val cpkDependencyNodes = cpkDependencyDocument.getElementsByTagName(DEPENDENCY_TAG)

            return ElementIterator(cpkDependencyNodes).asSequence().withIndex().mapNotNull { el ->
                val dependencyNameElements = el.value.getElementsByTagName(DEPENDENCY_NAME_TAG)
                val dependencyVersionElements = el.value.getElementsByTagName(DEPENDENCY_VERSION_TAG)
                val dependencyTypeElements = el.value.getElementsByTagName(DEPENDENCY_TYPE_TAG)
                val dependencySignersElements = el.value.getElementsByTagName(DEPENDENCY_SIGNERS_TAG)

                if (dependencyNameElements.length != 1) {
                    // Should already have been validated by schema.
                    val msg = fileLocationAppender(
                        "The entry at index ${el.index} of the CPK dependencies did not specify its name correctly.")
                    throw DependencyMetadataException(msg)
                }
                if (dependencyVersionElements.length != 1) {
                    // Should already have been validated by schema.
                    val msg = fileLocationAppender(
                        "The entry at index ${el.index} of the CPK dependencies file did not specify its version correctly.")
                    throw DependencyMetadataException(msg)
                }
                if (dependencySignersElements.length != 1) {
                    // Should already have been validated by schema.
                    val msg = fileLocationAppender(
                        "The entry at index ${el.index} of the CPK dependencies file did not specify its signers correctly.")
                    throw DependencyMetadataException(msg)
                }

                val signers = dependencySignersElements.item(0) as Element

                val publicKeySummaryHash: SecureHash =
                    ElementIterator(signers.getElementsByTagName(DEPENDENCY_SIGNER_TAG)).asSequence().map { signer ->
                        val algorithm = signer.getAttribute("algorithm").trim()
                        if (algorithm.isNullOrEmpty()) {
                            // Should already have been validated by schema.
                            val msg = fileLocationAppender(
                                "The entry at index ${el.index} of the CPK dependencies file" +
                                        " did not specify an algorithm for its signer's public key hash."
                            )
                            throw DependencyMetadataException(msg)
                        }
                        val hashData = Base64.getDecoder().decode(signer.textContent)
                        SecureHashImpl(algorithm, hashData)
                    }.summaryHash() ?:
                    if (signers.getElementsByTagName(DEPENDENCY_SAME_SIGNER_TAG).length == 1) {
                        // Use this until we can determine this CPK's own certificates.
                        SAME_SIGNER_PLACEHOLDER
                    } else {
                        throw IllegalStateException("No \"$DEPENDENCY_SIGNER_TAG\" or \"$DEPENDENCY_SAME_SIGNER_TAG\" elements found")
                    }
                CpkIdentifier(
                    dependencyNameElements.item(0).textContent,
                    dependencyVersionElements.item(0).textContent,
                    publicKeySummaryHash
                ).takeIf {
                    when (dependencyTypeElements.length) {
                        0 -> true
                        1 -> CpkType.parse(dependencyTypeElements.item(0).textContent) != CpkType.CORDA_API
                        else -> {
                            val msg = fileLocationAppender(
                                "The entry at index ${el.index} of the CPK dependencies file has multiple types")
                            throw DependencyMetadataException(msg)
                        }
                    }
                }
            }.toCollection(TreeSet())
        }

        @Suppress("ThrowsCount", "TooGenericExceptionCaught", "ComplexMethod")
        fun readLibraryConstraints(
            jarName: String,
            inputStream: InputStream,
            fileLocationAppender: (String) -> String
        ): NavigableMap<String, SecureHash> {
            val doc: Document = try {
                // The CPK library constraints are specified as an XML file.
                val documentBuilder = cpkV1DocumentBuilderFactory.newDocumentBuilder()
                documentBuilder.setErrorHandler(XmlErrorHandler(jarName))
                documentBuilder.parse(inputStream)
            } catch (e: DependencyMetadataException) {
                throw e
            } catch (e: SecurityException) {
                throw e
            } catch (e: Exception) {
                throw LibraryMetadataException(
                    fileLocationAppender("Error parsing $CPK_DEPENDENCY_CONSTRAINTS_FILE_ENTRY file"),
                    e
                )
            }

            val result = TreeMap<String, SecureHash>()
            ElementIterator(doc.getElementsByTagName(LIBRARY_CONSTRAINT_TAG)).asSequence().withIndex().map { el ->
                val libraryFilenameElements = el.value.getElementsByTagName(LIBRARY_FILENAME_TAG)
                val libraryHashElements = el.value.getElementsByTagName(LIBRARY_HASH_TAG)

                if (libraryFilenameElements.length != 1) {
                    val msg = fileLocationAppender(
                        "The entry at index ${el.index} of the $CPK_DEPENDENCY_CONSTRAINTS_FILE_ENTRY requires " +
                        "a single $LIBRARY_FILENAME_TAG")
                    throw LibraryMetadataException(msg)
                }
                if (libraryHashElements.length != 1) {
                    val msg = fileLocationAppender(
                        "The entry at index ${el.index} of the $CPK_DEPENDENCY_CONSTRAINTS_FILE_ENTRY requires a single $LIBRARY_HASH_TAG")
                    throw LibraryMetadataException(msg)
                }
                libraryFilenameElements.item(0).textContent to libraryHashElements.item(0).let {
                    val algorithmName = it.attributes.getNamedItem(LIBRARY_HASH_ALGORITHM_ATTRIBUTE).textContent
                    if (algorithmName != DigestAlgorithmName.SHA2_256.name) {
                        throw LibraryMetadataException(
                            fileLocationAppender(
                                "Expected ${DigestAlgorithmName.SHA2_256.name} in $CPK_DEPENDENCY_CONSTRAINTS_FILE_ENTRY," +
                                        " got '$algorithmName' instead"
                            )
                        )
                    }
                    SecureHashImpl(algorithmName, Base64.getDecoder().decode(it.textContent))
                }
            }.forEach { pair ->
                result.put("$CPK_LIB_FOLDER/${pair.first}", pair.second)?.let {
                    throw LibraryMetadataException(
                        fileLocationAppender(
                            "Found duplicate entry for library ${pair.first} of the $CPK_DEPENDENCY_CONSTRAINTS_FILE_ENTRY")
                    )
                }
            }
            return Collections.unmodifiableNavigableMap(result)
        }

        /**
         * [Iterator] for traversing every [Element] within a [NodeList].
         * Skips any [org.w3c.dom.Node] that is not also an [Element].
         */
        private class ElementIterator(private val nodes: NodeList) : Iterator<Element> {
            private var index = 0
            override fun hasNext() = index < nodes.length
            override fun next() = if (hasNext()) nodes.item(index++) as Element else throw NoSuchElementException()
        }

        private class XmlErrorHandler(private val jarName: String) : ErrorHandler {
            override fun warning(ex: SAXParseException) {
                logger.warn(
                    "Problem at (line {}, column {}) parsing CPK dependencies for {}: {}",
                    ex.lineNumber, ex.columnNumber, jarName, ex.message
                )
            }

            override fun error(ex: SAXParseException) {
                logger.error(
                    "Error at (line {}, column {}) parsing CPK dependencies for {}: {}",
                    ex.lineNumber, ex.columnNumber, jarName, ex.message
                )
                throw DependencyMetadataException(ex.message ?: "", ex)
            }

            override fun fatalError(ex: SAXParseException) {
                logger.error(
                    "Fatal error at (line {}, column {}) parsing CPK dependencies for {}: {}",
                    ex.lineNumber, ex.columnNumber, jarName, ex.message
                )
                throw ex
            }
        }

        private val cpkV1DocumentBuilderFactory = DocumentBuilderFactory.newInstance().also { dbf ->
            dbf.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true)
            dbf.disableProperty(XMLConstants.ACCESS_EXTERNAL_SCHEMA)
            dbf.disableProperty(XMLConstants.ACCESS_EXTERNAL_DTD)
            dbf.isExpandEntityReferences = false
            dbf.isIgnoringComments = true
            dbf.isNamespaceAware = true

            dbf.schema = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI).also { sf ->
                sf.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true)
                sf.disableProperty(XMLConstants.ACCESS_EXTERNAL_SCHEMA)
                sf.disableProperty(XMLConstants.ACCESS_EXTERNAL_DTD)
            }.newSchema(
                this::class.java.classLoader.getResource(CORDA_CPK_V1)
                    ?: throw IllegalStateException("Corda CPK v1 schema missing")
            )
        }

        private fun DocumentBuilderFactory.disableProperty(propertyName: String) {
            try {
                setAttribute(propertyName, "")
            } catch (_: IllegalArgumentException) {
                // Property not supported.
            }
        }

        private fun SchemaFactory.disableProperty(propertyName: String) {
            try {
                setProperty(propertyName, "")
            } catch (_: SAXNotRecognizedException) {
                // Property not supported.
            }
        }
    }
}