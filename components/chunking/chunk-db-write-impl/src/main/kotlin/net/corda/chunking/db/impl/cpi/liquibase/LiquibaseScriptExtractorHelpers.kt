package net.corda.chunking.db.impl.cpi.liquibase

import net.corda.db.admin.LiquibaseXmlConstants.DB_CHANGE_LOG_ROOT_ELEMENT
import net.corda.libs.cpi.datamodel.CpkDbChangeLog
import net.corda.libs.packaging.Cpk
import org.slf4j.LoggerFactory
import java.io.BufferedReader
import java.io.InputStream
import java.io.StringReader
import javax.xml.stream.XMLInputFactory
import javax.xml.stream.XMLStreamException
import javax.xml.stream.events.XMLEvent
import net.corda.libs.cpi.datamodel.CpkDbChangeLogIdentifier

/**
 * Some helper methods to extract the Liquibase XML from a [Cpk]
 */
@Suppress("unused_parameter")
class LiquibaseScriptExtractorHelpers {
    companion object {
        private const val MIGRATION_FOLDER = "migration"
        private val log = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    /**
     * For the given [content] string, test that:
     *
     * * it is XML
     * * it starts with the Liquibase root element `databaseChangeLog`
     * * the root element has the `xmlns` attribute set.
     */
    @Suppress("NestedBlockDepth")
    fun isLiquibaseXml(content: String): Boolean {
        val reader = XMLInputFactory.newInstance().createXMLStreamReader(StringReader(content))

        try {
            while (reader.hasNext()) {
                when (reader.next()) {
                    XMLEvent.START_ELEMENT -> {
                        if (reader.name.localPart == DB_CHANGE_LOG_ROOT_ELEMENT) {
                            val xmlns = reader.getAttributeValue(null, "xmlns")
                            if (xmlns != null) return true
                        }
                    }
                }
            }
        } catch (e: XMLStreamException) {
            log.error("Unexpected content in possible liquibase XML file", e)
            return false
        } catch (e: Exception) {
            log.error("Unexpected exception when parsing possible liquibase XML file", e)
            return false
        }

        return false
    }


    /**
     * For the given [Cpk] extract all Liquibase scripts that exist
     */
    fun readLiquibaseScripts(cpk: Cpk, inputStream: InputStream): List<CpkDbChangeLog> {
        log.info("Processing ${cpk.metadata.cpkId} for Liquibase scripts")
        val liquibaseScripts = mutableListOf<CpkDbChangeLog>()
        JarWalker.walk(inputStream) { path, it ->
            if (!isMigrationFile(path)) return@walk

            val xmlContent = read(path, it)

            if (!isXmlValid(path, xmlContent)) return@walk

            liquibaseScripts.add(
                CpkDbChangeLog(
                    CpkDbChangeLogIdentifier(cpk.metadata.fileChecksum, path),
                    xmlContent
                )
            )
        }
        log.info("Processing ${cpk.metadata.cpkId} for Liquibase scripts finished")
        return liquibaseScripts
    }

    /**
     * Is the file in `resources/migration` folder and has file extension `.xml` ?
     */
    private fun isMigrationFile(path: String) = path.lowercase()
        .endsWith(".xml") && (path.startsWith(MIGRATION_FOLDER) || path.startsWith("/$MIGRATION_FOLDER"))

    /**
     * Read the (expected) Liquibase XML script in the [inputStream]
     *
     * @return the XML as a [String] if successful otherwise an empty string
     */
    private fun read(path: String, inputStream: InputStream): String {
        val content = try {
            BufferedReader(inputStream.reader()).readText()
        } catch (e: Exception) {
            log.warn("Could not read as text: $path", e)
            String()
        }
        return content
    }

    /**
     * Validate the (expected) XML
     *
     * @return the XML as a [String] or null if it is invalid
     */
    private fun isXmlValid(path: String, content: String): Boolean {
        if (content.isEmpty() || content.isBlank()) {
            log.debug("Skipping empty XML string for $path")
            return false
        }

        if (!isLiquibaseXml(content)) {
            log.error("Could not read Liquibase file $path")
            return false
        }

        return true
    }
}
