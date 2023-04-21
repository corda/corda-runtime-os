package net.corda.db.admin.impl

import liquibase.resource.AbstractResource
import liquibase.resource.AbstractResourceAccessor
import liquibase.resource.ClassLoaderResourceAccessor
import liquibase.resource.Resource
import liquibase.resource.ResourceAccessor
import net.corda.db.admin.DbChange
import net.corda.db.admin.LiquibaseXmlConstants
import org.slf4j.LoggerFactory
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.StringWriter
import java.net.URI
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
        private val log = LoggerFactory.getLogger(this::class.java.enclosingClass)
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

    override fun close() {
        classLoaderResourceAccessor.close()
    }

    private fun createCompositeMasterChangeLog(): MutableList<Resource> {
        log.info("Creating composite master changelog XML file $masterChangeLogFileName with: ${dbChange.masterChangeLogFiles}")
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
                    log.debug("Generated composite master changelog XML file:\n$sw")
                }
            }

            return mutableListOf(
                StreamResource(
                    masterChangeLogFileName,
                    URI(masterChangeLogFileName),
                    ByteArrayInputStream(it.toByteArray()),
                    dbChange))
        }
    }

    override fun search(path: String?, recursive: Boolean): MutableList<Resource> {
        TODO("Not yet implemented")
    }

    override fun getAll(path: String?): MutableList<Resource> {
        if (path == null) {
            val msg = "getAll with null '$path' is not supported"
            log.error(msg)
            throw UnsupportedOperationException(msg)
        }

        // if we are accessing the specific path masterChangeLogFileName, we synthesise an XML file
        // which causes Liquibase to include all the changelogs we know about.
        if (masterChangeLogFileName == path)
            return createCompositeMasterChangeLog()

        if (path.contains("www.liquibase.org")) {
            // NOTE: this is needed for fetching the XML schemas
            log.debug("'$path' looks like an XML schema ... delegating to ClassLoaderResourceAccessor")
            return classLoaderResourceAccessor.getAll(path)
        }
        log.debug("Fetching change log from: $path relative")
        return mutableListOf(StreamResource(path, URI(path), dbChange.fetch(path), dbChange))
    }

    /**
     * Returns a description of the places this classloader will look for paths.
     * Used in error messages and other troubleshooting cases.
     */
    override fun describeLocations(): MutableList<String> {
        return (dbChange.changeLogFileList + masterChangeLogFileName)
            .map { "[${dbChange.javaClass.simpleName}]$it" }
            .toMutableList()
    }

    private class StreamResource(
        path: String,
        uri: URI,
        private val inputStream: InputStream,
        private val dbChange: DbChange
    ) : AbstractResource(path, uri) {
        override fun openInputStream(): InputStream {
            return inputStream
        }

        override fun exists(): Boolean {
            return true
        }

        override fun resolve(other: String?): Resource {
            TODO("Not yet implemented")
        }

        override fun resolveSibling(other: String?): Resource {
            val resolvedPath = resolveSiblingPath(other)
            return StreamResource(resolvedPath, URI(resolvedPath), dbChange.fetch(resolvedPath), dbChange)
        }
    }
}
