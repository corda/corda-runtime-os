package net.corda.chunking.db.impl.cpi.liquibase

import net.corda.db.admin.LiquibaseXmlConstants.DB_CHANGE_LOG_ROOT_ELEMENT
import net.corda.libs.cpi.datamodel.CpkDbChangeLogEntity
import net.corda.libs.cpi.datamodel.CpkDbChangeLogKey
import net.corda.libs.packaging.Cpk
import net.corda.v5.base.util.contextLogger
import java.io.BufferedReader
import java.io.InputStream
import java.io.StringReader
import javax.xml.stream.XMLInputFactory
import javax.xml.stream.XMLStreamException
import javax.xml.stream.events.XMLEvent

/**
 * Some helper methods to extract the Liquibase XML from a [Cpk]
 */
@Suppress("unused_parameter")
class LiquibaseExtractorHelpers {
    companion object {
        private const val MIGRATION_FOLDER = "migration"
        private val log = contextLogger()
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
     * For the given [Cpk] extract all Liquibase scripts that exist and convert
     * them into entities that we can persist to the database.
     */
    fun getEntities(cpk: Cpk, inputStream: InputStream): List<CpkDbChangeLogEntity> {
        log.info("Processing ${cpk.metadata.cpkId} for Liquibase files")
        val entities = mutableListOf<CpkDbChangeLogEntity>()
        JarWalker.walk(inputStream) { path, it ->
            if (!isMigrationFile(path)) return@walk
            val content = validateXml(path, it) ?: return@walk
            entities.add(createEntity(cpk, path, content))
        }
        log.info("Processing ${cpk.metadata.cpkId} for Liquibase files finished")
        return entities
    }

    /**
     * Is the file in `resources/migration` folder and has file extension `.xml` ?
     */
    private fun isMigrationFile(path: String) = path.lowercase()
        .endsWith(".xml") && (path.startsWith(MIGRATION_FOLDER) || path.startsWith("/$MIGRATION_FOLDER"))

    /**
     * Validate the (expected) XML in the [inputStream] is Liquibase
     *
     * @return the XML as a [String] or null if it is invalid
     */
    private fun validateXml(path: String, inputStream: InputStream): String? {
        val content = try {
            BufferedReader(inputStream.reader()).readText()
        } catch (e: Exception) {
            log.warn("Could not read as text: $path", e)
            return null
        }

        if (content.isEmpty() || content.isBlank()) {
            log.debug("Skipping empty XML string for $path")
            return null
        }

        if (!isLiquibaseXml(content)) {
            log.error("Could not read Liquibase file $path")
            return null
        }

        return content
    }

    /**
     * Create db entity containing the Liquibase script for the given [Cpk]
     */
    private fun createEntity(cpk: Cpk, path: String, xmlContent: String): CpkDbChangeLogEntity {
        val id = CpkDbChangeLogKey(cpk.metadata.fileChecksum.toString(), path)
        return CpkDbChangeLogEntity(id, xmlContent)
    }
}
