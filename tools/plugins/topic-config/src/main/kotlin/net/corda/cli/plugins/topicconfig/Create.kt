package net.corda.cli.plugins.topicconfig

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator
import com.fasterxml.jackson.module.kotlin.KotlinFeature
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.fasterxml.jackson.module.kotlin.readValue
import java.net.URL
import java.net.URLDecoder
import java.nio.charset.Charset
import java.util.jar.JarEntry
import java.util.jar.JarFile
import picocli.CommandLine
import java.nio.file.Files
import java.nio.file.Paths

@CommandLine.Command(
    name = "create",
    description = ["Create Kafka topics"],
    subcommands = [Preview::class, CreateConnect::class],
    mixinStandardHelpOptions = true
)
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
        names = ["-p", "--partitions"],
        description = ["Override partition count globally"]
    )
    var partitionOverride: Int = 1

    @CommandLine.Option(
        names = ["-o", "--overrides"],
        description = ["Relative path of override Kafka topic configuration file in YAML format"]
    )
    var overrideFilePath: String? = null

    @CommandLine.Option(
        names = ["-u", "--user"],
        description = ["One or more Corda workers and their respective Kafka users e.g. -u crypto=Charlie -u rest=Rob"]
    )
    var kafkaUsers: Map<String, String> = emptyMap()

    data class TopicConfig(
        val name: String,
        val consumers: List<String>,
        val producers: List<String>,
        val config: Map<String, String> = emptyMap()
    )

    data class TopicDefinitions(
        val topics: Map<String, TopicConfig>
    )

    data class PreviewTopicConfigurations(
        val topics: List<PreviewTopicConfiguration>,
        val acls: List<PreviewTopicACL>
    )

    data class OverrideTopicConfigurations(
        val topics: List<OverrideTopicConfiguration>,
        val acls: List<PreviewTopicACL>
    )

    data class PreviewTopicConfiguration(
        val name: String,
        val partitions: Int,
        val replicas: Short,
        val config: Map<String, String> = emptyMap()
    )

    data class OverrideTopicConfiguration(
        val name: String,
        val partitions: Int?,
        val replicas: Short?,
        val config: Map<String, String> = emptyMap()
    )

    data class PreviewTopicACL(
        val topic: String,
        val users: List<UserConfig>
    )

    data class UserConfig(
        val name: String,
        val operations: List<String>
    )

    val mapper: ObjectMapper = ObjectMapper(YAMLFactory()
        .enable(YAMLGenerator.Feature.MINIMIZE_QUOTES)
        .enable(YAMLGenerator.Feature.LITERAL_BLOCK_STYLE)
        .disable(YAMLGenerator.Feature.WRITE_DOC_START_MARKER))
        .registerModule(KotlinModule.Builder()
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
        "flowMapper" to listOf("flowMapper", "combined"),
        "verification" to listOf("verification", "combined"),
        "membership" to listOf("membership", "combined"),
        "gateway" to listOf("p2pGateway", "combined"),
        "link-manager" to listOf("p2pLinkManager", "combined"),
        "persistence" to listOf("persistence", "combined"),
        "tokenSelection" to listOf("tokenSelection", "combined"),
        "rest" to listOf("rest", "combined"),
        "uniqueness" to listOf("uniqueness", "combined")
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

    fun getTopicConfigsForPreview(): PreviewTopicConfigurations {
        return applyOverrides(getTopicConfigsForPreview(getTopicConfigs()))
    }

    fun applyOverrides(config: PreviewTopicConfigurations) : PreviewTopicConfigurations =
        if (overrideFilePath == null) {
            config
        } else {
            mergeConfigurations(config, mapper.readValue(Files.readString(Paths.get(overrideFilePath!!))))
        }

    fun getTopicConfigsForPreview(topicConfigurations: List<TopicConfig>): PreviewTopicConfigurations {
        val topicConfigs = mutableListOf<PreviewTopicConfiguration>()
        val acls = mutableListOf<PreviewTopicACL>()

        topicConfigurations.forEach { topicConfig ->
            val topicName = getTopicName(topicConfig)
            topicConfigs.add(PreviewTopicConfiguration(topicName, partitionOverride, replicaOverride, topicConfig.config))

            val usersReadAccess = getUsersForProcessors(topicConfig.consumers)
            val usersWriteAccess = getUsersForProcessors(topicConfig.producers)

            val users = (usersReadAccess + usersWriteAccess).toSet().map {
                val operations = mutableListOf("describe")
                if (it in usersWriteAccess)
                    operations.add("write")
                if (it in usersReadAccess)
                    operations.add("read")
                UserConfig(it, operations.reversed())
            }

            acls.add(PreviewTopicACL(topicName, users))
        }

        return PreviewTopicConfigurations(topicConfigs, acls)
    }

    private fun mergeConfigurations(source: PreviewTopicConfigurations, overrides: OverrideTopicConfigurations) =
        PreviewTopicConfigurations(
            overrides.topics.fold(source.topics, ::mergeTopicConfiguration),
            overrides.acls.fold(source.acls, ::mergeTopicACL)
        )

    private fun mergeTopicConfiguration(source: List<PreviewTopicConfiguration>, override: OverrideTopicConfiguration) =
        source.map {
            if (it.name == override.name) {
                PreviewTopicConfiguration(
                    it.name,
                    override.partitions ?: it.partitions,
                    override.replicas ?: it.replicas,
                    it.config + override.config
                )
            } else {
                it
            }
        }.toList()

    private fun mergeTopicACL(source: List<PreviewTopicACL>, override: PreviewTopicACL) =
        source.map {
            if (it.topic == override.topic) PreviewTopicACL(it.topic, it.users + override.users) else it
        }.toList()

}
