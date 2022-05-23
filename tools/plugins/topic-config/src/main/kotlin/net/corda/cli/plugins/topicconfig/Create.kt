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

@CommandLine.Command(name = "create", description = ["Generates schema scripts for kafka topics"])
class Create(
    private val writerFactory: (String) -> FileWriter = { file -> FileWriter(File(file)) },
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
    var bootstrapAddr: String? = null

    @CommandLine.Option(
        names = ["-l", "--location"],
        description = ["Directory to write all files to"]
    )
    var outputLocation: String? = null

    companion object {
        private val logger: Logger = LoggerFactory.getLogger(this::class.java)
    }

    override fun run() {
        val cl = Topic.classLoader
        val mapper = ObjectMapper(YAMLFactory()).registerModule(
            KotlinModule.Builder()
                .withReflectionCacheSize(512)
                .configure(KotlinFeature.NullToEmptyCollection, true)
                .configure(KotlinFeature.NullToEmptyMap, true)
                .configure(KotlinFeature.NullIsSameAsDefault, false)
                .configure(KotlinFeature.SingletonSupport, false)
                .configure(KotlinFeature.StrictNullChecks, false)
                .build()
        )
        val files: List<URL> = cl.getResources("net/corda/schema").toList().filterNotNull()
        val filesAndContents: Map<String, TopicDefinitions> = files.filter { it.protocol == "jar" }
            .flatMap { file ->
                val jarPath = file.path.substringAfter("file:").substringBeforeLast("!")
                val jar = JarFile(URLDecoder.decode(jarPath, Charset.defaultCharset()))
                val yamlFiles = jar.entries().toList().map { entry ->
                    entry
                }.filter { it.name.endsWith(".yml") || it.name.endsWith(".yaml") }

                return@flatMap yamlFiles.map { entry: JarEntry ->
                    val data: String = jar.getInputStream(entry)
                        .bufferedReader(Charset.defaultCharset()).use { it.readText() }
                    val parsedData: TopicDefinitions = mapper.readValue(data)
                    return@map entry.name to parsedData
                }
            }.toMap()
        val topics = filesAndContents.values.flatMap {
            it.topics.values
        }.flatMap {
            val topicConfig = it
            // Add support for prefixed topic names
            val topicPrefix = topicPrefix ?: ""
            val topicName = if (topicPrefix.isEmpty()) topicConfig.name else "$topicPrefix-${topicConfig.name}"
            // Global partition count override
            val partitions = partitionOverride ?: 1
            // Global replica count override
            val replicas = replicaOverride ?: 1
            val config = createConfigString(topicConfig.config)
            val acls = it.consumers.map { consumer ->
                "kafka-acls.sh --bootstrap-server $bootstrapAddr --add --allow-principal User:$consumer --operation read --topic $topicName"
            } + it.producers.map { producer ->
                "kafka-acls.sh --bootstrap-server $bootstrapAddr --add --allow-principal User:$producer --operation write --topic $topicName"
            }
            return@flatMap listOf(
                "kafka-topics.sh --bootstrap-server $bootstrapAddr --partitions $partitions --replication-factor $replicas --create --if-not-exists --topic $topicName $config &"
            ) + acls
        } + "wait"

        if (outputLocation != null) {
            println("Wrote to path $outputLocation")
            val writer = writerFactory(outputLocation!!)
            writer.write(topics.joinToString(System.lineSeparator()))
            writer.flush()
            writer.close()
        } else {
            println(topics.joinToString(System.lineSeparator()))
        }
    }

    private fun createConfigString(config: Map<String, String>): String {
        if (config.entries.isNotEmpty()) {
            val values = config.entries.map { configEntry ->
                return@map "--config \"${configEntry.key}=${configEntry.value}\""
            }.joinToString(" ")
            return values
        } else {
            return ""
        }
    }
}
