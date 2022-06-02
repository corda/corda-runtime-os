package net.corda.cli.plugins.topicconfig

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.module.kotlin.KotlinFeature
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.fasterxml.jackson.module.kotlin.readValue
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import picocli.CommandLine
import java.io.File
import java.io.FileWriter
import java.net.URL
import java.net.URLDecoder
import java.nio.charset.Charset
import java.util.jar.JarEntry
import java.util.jar.JarFile

data class TopicConfig(
    val name: String,
    val consumers: List<String>,
    val producers: List<String>,
    val config: Map<String, String> = emptyMap()
)

data class TopicDefinitions(
    val topics: Map<String, TopicConfig>
)

class NoValidBootstrapAddress(address: String?) : Exception("No valid bootstrap address was specified - got '$address'")

@CommandLine.Command(name = "create", description = ["Generates schema scripts for kafka topics"])
class Create(
    private val writerFactory: (String) -> FileWriter = { file -> FileWriter(File(file)) },
    private val cl: ClassLoader = Topic.classLoader,
    private val resourceGetter: (String) -> List<URL> = { path -> cl.getResources(path).toList().filterNotNull() }
) : Runnable {
    @CommandLine.Option(
        names = ["-r", "--replicas"],
        description = ["Override replica count globally"]
    )
    var replicaOverride: Int? = null
    @CommandLine.Option(
        names = ["-p", "--partitions"],
        description = ["Override partition count globally"]
    )
    var partitionOverride: Int? = null
    @CommandLine.Option(
        names = ["-f", "--prefix"],
        description = ["Set topic prefix for created topics"]
    )
    var topicPrefix: String? = null
    @CommandLine.Option(
        names = ["-a", "--address"],
        description = ["Bootstrap server address for topic create"],
        required = true
    )
    var bootstrapAddress: String? = null

    @CommandLine.Option(
        names = ["-l", "--location"],
        description = ["Directory to write all files to"]
    )
    var outputLocation: String? = null

    @CommandLine.Option(
        names = ["-b", "--block-size"],
        description = ["Number of simultaneous topic creations to be ran"]
    )
    var blockSize: Int = 6

    companion object {
        private val logger: Logger = LoggerFactory.getLogger(this::class.java)
    }

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

    fun createTopicScripts(
        topicName: String,
        partitions: Int,
        replicas: Int,
        config: Map<String, String>
    ): List<String> {
        val address = bootstrapAddress ?: throw NoValidBootstrapAddress(bootstrapAddress)
        @Suppress("MaxLineLength")
        return listOf("kafka-topics.sh --command-config /tmp/working_dir/config.properties --bootstrap-server $address --partitions $partitions --replication-factor $replicas --create --if-not-exists --topic $topicName ${createConfigString(config)} &")
    }

    fun createConfigString(config: Map<String, String>): String {
        if (config.entries.isNotEmpty()) {
            val values = config.entries.map { configEntry ->
                "--config \"${configEntry.key}=${configEntry.value}\""
            }.joinToString(" ")
            return values
        } else {
            return ""
        }
    }

    fun createACLs(topic: String, consumers: List<String>, producers: List<String>): List<String> {
        val address = bootstrapAddress ?: throw NoValidBootstrapAddress(bootstrapAddress)
        val readACLs = consumers.map { consumer ->
            "kafka-acls.sh --bootstrap-server $address --add --allow-principal User:$consumer --operation read --topic $topic"
        }
        val writeACLs = producers.map { producer ->
            @Suppress("MaxLineLength")
            "kafka-acls.sh --bootstrap-server $address --add --allow-principal User:$producer --operation write --topic $topic"
        }

        return readACLs + writeACLs
    }

    override fun run() {
        val files: List<URL> = resourceGetter("net/corda/schema")

        @Suppress("UNCHECKED_CAST")
        val topicDefinitions: List<TopicDefinitions> =
            extractResourcesFromJars(files, listOf("yml", "yaml")).values.toList() as List<TopicDefinitions>
        val topicConfigs = topicDefinitions.flatMap { it: TopicDefinitions ->
            it.topics.values
        }
        val topics = topicConfigs.flatMap { topicConfig: TopicConfig ->
            // Add support for prefixed topic names
            val topicPrefix = topicPrefix ?: ""
            val topicName = if (topicPrefix.isEmpty()) topicConfig.name else "$topicPrefix-${topicConfig.name}"
            // Global partition count override
            val partitions = partitionOverride ?: 1
            // Global replica count override
            val replicas = replicaOverride ?: 1
            val config = topicConfig.config
            val topicScripts = createTopicScripts(topicName, partitions, replicas, config)
            val acls = createACLs(topicName, topicConfig.consumers, topicConfig.producers)
            topicScripts + acls
        }

        val finalTopics = topics.flatMapIndexed{ index: Int, topic: String ->
            if (index % blockSize == 0) {
                listOf(topic, "wait")
            } else {
                listOf(topic)
            }
        }

        if (outputLocation != null) {
            logger.info("Wrote to path $outputLocation")
            val writer = writerFactory(outputLocation!!)
            writer.write(finalTopics.joinToString(System.lineSeparator()))
            writer.flush()
            writer.close()
        } else {
            println(finalTopics.joinToString(System.lineSeparator()))
        }
    }
}
