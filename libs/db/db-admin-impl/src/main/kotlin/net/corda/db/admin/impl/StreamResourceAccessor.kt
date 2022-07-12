package net.corda.db.admin.impl

import liquibase.resource.AbstractResourceAccessor
import liquibase.resource.ClassLoaderResourceAccessor
import liquibase.resource.InputStreamList
import liquibase.resource.ResourceAccessor
import net.corda.db.admin.DbChange
import net.corda.db.admin.LiquibaseXmlConstants
import net.corda.v5.base.util.contextLogger
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.StringWriter
import java.net.URI
import java.util.SortedSet
import java.util.TreeSet
import javax.xml.XMLConstants.ACCESS_EXTERNAL_DTD
import javax.xml.XMLConstants.ACCESS_EXTERNAL_STYLESHEET
import javax.xml.XMLConstants.FEATURE_SECURE_PROCESSING
import javax.xml.XMLConstants.W3C_XML_SCHEMA_INSTANCE_NS_URI
import javax.xml.stream.XMLOutputFactory
import javax.xml.transform.OutputKeys
import javax.xml.transform.TransformerFactory
import javax.xml.transform.stream.StreamResult
import javax.xml.transform.stream.StreamSource

/**
 * Implementation of [liquibase.resource.ResourceAccessor] that handles with
 * Stream resources directly and supports aggregating master changelogs.
 * Getting the streams is delegated to [dbChange].
 *
 * @property masterChangeLogFileName is the name of the (generated) master log file
 * @property dbChange
 * @constructor Create empty Stream resource accessor
 */
class StreamResourceAccessor(
    private val masterChangeLogFileName: String,
    private val dbChange: DbChange,
    private val classLoaderResourceAccessor: ResourceAccessor =
        ClassLoaderResourceAccessor(ClassLoaderResourceAccessor::class.java.classLoader)
) : AbstractResourceAccessor() {
    companion object {
        private val log = contextLogger()
    }

    // transformer only used for debugging
    private val transformerFactory by lazy {
        TransformerFactory.newInstance().apply {
            setFeature(FEATURE_SECURE_PROCESSING, true)
            try {
                setAttribute(ACCESS_EXTERNAL_STYLESHEET, "")
            } catch (e: IllegalArgumentException) {
                // attribute not supported
            }
            try {
                setAttribute(ACCESS_EXTERNAL_DTD, "")
            } catch (e: IllegalArgumentException) {
                // attribute not supported
            }
        }
    }

    /**
     * Return the streams for each resource mapped by the given path.
     * The path is often a URL but does not have to be.
     * Should accept both / and \ chars for file paths to be platform-independent.
     * If path points to a compressed resource, return a stream of the uncompressed contents of the file.
     * Returns [InputStreamList] since multiple resources can map to the same path, such as "META-INF/MAINFEST.MF".
     * Remember to close streams when finished with them.
     *
     * @param relativeTo Location that streamPath should be found relative to. If null, streamPath is an absolute path
     * @return Empty list if the resource does not exist.
     * @throws IOException if there is an error reading an existing path.
     */
    override fun openStreams(relativeTo: String?, streamPath: String?): InputStreamList {
        if (masterChangeLogFileName == streamPath) {
            return createCompositeMasterChangeLog()
        }
        if (null != streamPath && streamPath.contains("www.liquibase.org")) {
            // NOTE: this is needed for fetching the XML schemas
            log.debug("'$streamPath' looks like an XML schema ... delegating to ClassLoaderResourceAccessor")
            return classLoaderResourceAccessor.openStreams(relativeTo, streamPath)
        }
        if (null == relativeTo && null != streamPath) {
            log.debug("Fetching change log from: $streamPath")
            return InputStreamList(URI(streamPath), dbChange.fetch(streamPath, relativeTo))
        }

        throw UnsupportedOperationException(
            "openStreams with arguments '$relativeTo' and '$streamPath' not supported"
        )
    }

    private fun createCompositeMasterChangeLog(): InputStreamList {
        log.info("Creating composite master changelog file $masterChangeLogFileName with: ${dbChange.masterChangeLogFiles}")
        // dynamically create the master file by combining the specified.
        ByteArrayOutputStream().use {
            val xmlWriter = XMLOutputFactory.newInstance().createXMLStreamWriter(it,"UTF-8")
            xmlWriter.writeStartDocument("UTF-8", "1.0")
            xmlWriter.writeStartElement(LiquibaseXmlConstants.DB_CHANGE_LOG_ROOT_ELEMENT)
            xmlWriter.writeDefaultNamespace(LiquibaseXmlConstants.DB_CHANGE_LOG_NS)
            xmlWriter.writeNamespace("xsi", W3C_XML_SCHEMA_INSTANCE_NS_URI)
            xmlWriter.writeAttribute(
                W3C_XML_SCHEMA_INSTANCE_NS_URI,
                "schemaLocation",
                "${LiquibaseXmlConstants.DB_CHANGE_LOG_NS} ${LiquibaseXmlConstants.DB_CHANGE_LOG_XSD}"
            )

            dbChange.masterChangeLogFiles.forEach { f ->
                xmlWriter.writeStartElement(LiquibaseXmlConstants.DB_CHANGE_LOG_INCLUDE_ELEMENT)
                xmlWriter.writeAttribute(LiquibaseXmlConstants.DB_CHANGE_LOG_INCLUDE_FILE_ATTRIBUTE, f)
                xmlWriter.writeEndElement()
            }

            xmlWriter.writeEndElement()
            xmlWriter.flush()

            if(log.isDebugEnabled) {
                val transformer = transformerFactory.newTransformer()
                transformer.setOutputProperty(OutputKeys.INDENT, "yes")
                transformer.setOutputProperty(OutputKeys.STANDALONE, "yes")
                StringWriter().use { sw ->
                    transformer.transform(StreamSource(ByteArrayInputStream(it.toByteArray())), StreamResult(sw))
                    log.debug("Generated Master XML:\n$sw")
                }
            }

            return InputStreamList(URI(masterChangeLogFileName), ByteArrayInputStream(it.toByteArray()))
        }
    }

    /**
     * Returns the path to all resources contained in the given path.
     * The passed path is not included in the returned set.
     * Returned strings should use "/" for file path separators, regardless of the OS and should accept both / and \
     * chars for file paths to be platform-independent.
     * Returned set is sorted, normally alphabetically but subclasses can use different comparators.
     * The values returned should be able to be passed into [.openStreams] and return the contents.
     * Returned paths should normally be root-relative and therefore not be an absolute path, unless there is a good
     * reason to be absolute.
     *
     *
     * @param relativeTo Location that streamPath should be found relative to. If null, path is an absolute path
     * @param path The path to lookup resources in.
     * @param recursive Set to true and will return paths to contents in sub directories as well.
     * @param includeFiles Set to true and will return paths to files.
     * @param includeDirectories Set to true and will return paths to directories.
     * @return empty set if nothing was found
     * @throws IOException if there is an error reading an existing root.
     */
    override fun list(
        relativeTo: String?,
        path: String?,
        recursive: Boolean,
        includeFiles: Boolean,
        includeDirectories: Boolean
    ): SortedSet<String> {
        // NOTE: this method doesn't seem to be used by Liquibase internally, hence not implemented.
        TODO("Not yet implemented")
    }

    /**
     * Returns a description of the places this classloader will look for paths.
     * Used in error messages and other troubleshooting cases.
     */
    override fun describeLocations(): SortedSet<String> {
        return (dbChange.changeLogFileList + masterChangeLogFileName)
            .mapTo(TreeSet()) { "[${dbChange.javaClass.simpleName}]$it" }
    }
}
