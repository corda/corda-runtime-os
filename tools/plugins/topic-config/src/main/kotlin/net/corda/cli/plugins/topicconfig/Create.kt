package net.corda.cli.plugins.topicconfig

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.module.kotlin.KotlinFeature
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.fasterxml.jackson.module.kotlin.readValue
import picocli.CommandLine
import java.net.URL
import java.net.URLDecoder
import java.nio.charset.Charset
import java.util.jar.JarEntry
import java.util.jar.JarFile

@CommandLine.Command(name = "create", description = ["Create Kafka topics"], subcommands = [CreateScript::class, CreateConnect::class])
class Create(
    private val cl: ClassLoader = TopicPlugin.classLoader,
    private val resourceGetter: (String) -> List<URL> = { path -> cl.getResources(path).toList().filterNotNull() }
) {

    @CommandLine.ParentCommand
    var topic: TopicPlugin.Topic? = null

    @CommandLine.Option(
        names = ["-r", "--replicas"],
        description = ["Override replica count globally"]
    )
    var replicaOverride: Short = 1

    @CommandLine.Option(
        names = ["-u", "--user"],
        description = ["One or more Corda workers and their respective Kafka users e.g. -u crypto=Charlie -u rest=Rob"]
    )
    var kafkaUsers: Map<String, String> = emptyMap()

    @CommandLine.Option(
        names = ["-c", "--counts"],
        description = ["One or more Corda workers and their respective replica counts e.g. -c crypto=3 -c rest=5"]
    )
    var workerCounts: Map<String, Int> = emptyMap()

    data class TopicConfig(
        val name: String,
        val consumers: List<String>,
        val producers: List<String>,
        val config: Map<String, String> = emptyMap()
    )

    data class TopicDefinitions(
        val topics: Map<String, TopicConfig>
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

    private val workersForProcessor = mapOf(
        "crypto" to listOf("crypto", "combined"),
        "db" to listOf("db", "combined"),
        "flow" to listOf("flow", "combined"),
        "membership" to listOf("membership", "combined"),
        "gateway" to listOf("p2pGateway", "combined"),
        "link-manager" to listOf("p2pLinkManager", "combined"),
        "rest" to listOf("rest", "combined"),
        "uniqueness" to listOf("db", "combined")
    )

    fun getTopicConfigs(): List<TopicConfig> {
        val files: List<URL> = resourceGetter("net/corda/schema")

        @Suppress("UNCHECKED_CAST")
        val topicDefinitions: List<TopicDefinitions> =
            extractResourcesFromJars(files, listOf("yml", "yaml")).values.toList() as List<TopicDefinitions>
        val topicConfigs = topicDefinitions.flatMap { it: TopicDefinitions ->
            it.topics.values
        }
        return topicConfigs
    }

    fun getTopicName(topicConfig: TopicConfig): String {
        return "${topic!!.namePrefix}${topicConfig.name}"
    }

    fun getUsersForProcessors(processors: List<String>): Set<String> {
        return processors.map { processor -> getUsersForProcessor(processor) }.flatten().toSet()
    }

    private fun getUsersForProcessor(processor: String): Set<String> {
        val workers = workersForProcessor[processor] ?: throw IllegalStateException("Unknown processor $processor")
        return workers.mapNotNull { worker -> kafkaUsers[worker] }.toSet()
    }

    fun getPartitionsForTopics(topicConfigs: List<TopicConfig>): Map<String, Int> {
        val topicConfigMap = topicConfigs.associateBy { it.name }
        return topicConfigMap.mapValues { getPartitionsForTopic(it.value, topicConfigMap) }
    }
    private fun getPartitionsForTopic(topicConfig: Create.TopicConfig, topicConfigMap: Map<String, Create.TopicConfig>): Int =
        if (topicConfig.name.endsWith(".dlq")) 1
        else if (topicConfig.name.endsWith(".state")) {
            val eventTopicName = topicConfig.name.slice(0 until topicConfig.name.length - ".state".length)
            val eventTopicConfig = topicConfigMap[eventTopicName]
                ?: throw IllegalStateException("No corresponding event topic for state topic ${topicConfig.name}")
            getPartitionsForTopic(eventTopicConfig, topicConfigMap)
        } else if (topicConfig.config["cleanup.policy"] == "compact") 1
        else topicConfig.consumers.sumOf { getWorkerCountForProcessor(it) }

    private fun getWorkerCountForProcessor(processor: String): Int {
        val workers = workersForProcessor[processor] ?: throw IllegalStateException("Unknown processor $processor")
        return workers.sumOf { workerCounts[it] ?: 0 }
    }

    fun collectJars(
        files: List<URL>,
        jarFactory: (String) -> JarFile = ::JarFile
    ): List<JarFile> {
        return files
            .filter { it.protocol == "jar" }
            .map {
                val jarPath = it.path.substringAfter("file:").substringBeforeLast("!")
                jarFactory(URLDecoder.decode(jarPath, Charset.defaultCharset()))
            }
    }

    fun extractResourcesFromJars(
        files: List<URL>,
        extensions: List<String>,
        jars: List<JarFile> = collectJars(files),
        getEntries: (JarFile) -> List<JarEntry> = { jar: JarFile -> jar.entries().toList() }
    ): Map<String, *> {
        return jars.flatMap { jar: JarFile ->
            val yamlFiles = getEntries(jar).filter {
                extensions.contains(it.name.substringAfterLast("."))
            }

            yamlFiles.map { entry: JarEntry ->
                val data: String = jar.getInputStream(entry)
                    .bufferedReader(Charset.defaultCharset()).use { it.readText() }
                val parsedData: TopicDefinitions = mapper.readValue(data)
                entry.name to parsedData
            }
        }.toMap()
    }

}
