package net.corda.application.dbsetup

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.module.kotlin.KotlinFeature
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.fasterxml.jackson.module.kotlin.readValue
import net.corda.messagebus.db.datamodel.TopicEntry
import net.corda.utilities.debug
import org.osgi.framework.FrameworkUtil
import org.slf4j.LoggerFactory
import java.nio.charset.Charset
import java.sql.Connection

class DbMessageBusSetup {
    companion object{
        // At the moment it's not easy to create partitions, so default value increased to 3 until tooling is available
        // (There are multiple consumers using the same group for some topics and some stay idle if there is only 1 partition)
        const val defaultNumPartitions = 3
        private val log = LoggerFactory.getLogger(DbMessageBusSetup::class.java)
    }
    /** creates the topics for the INMEMORY or DATABASE db message bus
     * @param messageBusConnection
    * */
    fun createTopicsOnDbMessageBus(messageBusConnection: Connection) {
        val bundle = FrameworkUtil.getBundle(net.corda.schema.Schemas::class.java)
        log.debug { "Got bundle $bundle for class (net.corda.schema.Schemas::class.java)" }
        val paths = bundle.getEntryPaths("net/corda/schema").toList()
        log.debug { "Entry paths found at path \"net/corda/schema\" = $paths" }
        val resources = paths.filter { it.endsWith(".yaml") }.map {
            bundle.getResource(it)
        }
        log.debug { "Mapping bundle resources where path suffix = \".yaml\" to topicDefinitions, resources = $resources" }
        val topicDefinitions = resources.map {
            val data: String = it.openStream()
                .bufferedReader(Charset.defaultCharset()).use { it.readText() }
            val parsedData: TopicDefinitions = mapper.readValue(data)
            parsedData
        }
        val topicConfigs = topicDefinitions.flatMap { it: TopicDefinitions ->
            it.topics.values
        }
        log.debug { "Mapping topicDefinitions to topicConfigs, topicDefinitions = $topicDefinitions \ntopicConfigs = $topicDefinitions" }
        messageBusConnection.use { connection ->
            topicConfigs.map {
                connection.createStatement().execute(
                    TopicEntry(it.name, defaultNumPartitions).toInsertStatement()
                )
            }
            if (!connection.autoCommit) {
                connection.commit()
            }
        }
    }
    data class TopicDefinitions(
        val topics: Map<String, TopicConfig>
    )

    data class TopicConfig(
        val name: String,
        val consumers: List<String>,
        val producers: List<String>,
        val config: Map<String, String> = emptyMap()
    )

    private val mapper: ObjectMapper = ObjectMapper(YAMLFactory()).registerModule(
        KotlinModule.Builder()
            .withReflectionCacheSize(512)
            .configure(KotlinFeature.NullToEmptyCollection, true)
            .configure(KotlinFeature.NullToEmptyMap, true)
            .configure(KotlinFeature.NullIsSameAsDefault, false)
            .configure(KotlinFeature.SingletonSupport, false)
            .configure(KotlinFeature.StrictNullChecks, false)
            .build()
    )
}