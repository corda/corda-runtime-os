package net.corda.schema

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.module.kotlin.KotlinFeature
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.fasterxml.jackson.module.kotlin.readValue
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.io.File
import kotlin.reflect.KProperty1
import kotlin.reflect.full.companionObject
import kotlin.reflect.full.memberProperties
import kotlin.reflect.typeOf

class SchemaTests {
    // Setup jackson mapper to support yaml with null values
    private val mapper = ObjectMapper(YAMLFactory()).registerModule(
        KotlinModule.Builder()
            .withReflectionCacheSize(512)
            .configure(KotlinFeature.NullToEmptyCollection, true)
            .configure(KotlinFeature.NullToEmptyMap, true)
            .configure(KotlinFeature.NullIsSameAsDefault, false)
            .configure(KotlinFeature.SingletonSupport, false)
            .configure(KotlinFeature.StrictNullChecks, false)
            .build()
    )

    @Suppress("UNCHECKED_CAST")
    private val yamlFileData: Map<String, Map<String, Map<String, Map<String, *>>>> by lazy {
        // Scan resources in classpath to find all the yaml files to scan
        this::class.java.classLoader.getResources("net/corda/schema")
            .toList()
            .filterNotNull()
            .map { File(it.toURI()) }
            .filter { it.isDirectory }
            .flatMap { it.listFiles()!!.toList() }
            .filter { it.name.endsWith("yaml") || it.endsWith("yml") }
            .associate { it.name to mapper.readValue(it.readText()) as Map<String, *> }
            as Map<String, Map<String, Map<String, Map<String, *>>>>
    }

    // Scan companion objects for constant definitions
    private val memberMap by lazy {
        Schemas::class.nestedClasses
            .filter { it.simpleName != null && !it.isCompanion }
            .associate { it.simpleName!! to (it.companionObject ?: it) }
            .mapValues { (_, cls) ->
                cls.memberProperties
                    .filter { property ->
                        property.returnType == typeOf<String>()
                    }.map { property ->
                        @Suppress("UNCHECKED_CAST")
                        property as KProperty1<Any?, String>
                    }
                    .map { property ->
                        if (property.isConst && !cls.isCompanion) {
                            property.call()
                        } else {
                            property.get(cls.objectInstance)
                        }
                    }
            }
    }

    @Test
    fun `Ensure every schema class has a yaml file`() {
        val classes = memberMap.keys
        val filesWithoutExtensions = yamlFileData.keys.map { it.substringBeforeLast(".") }
        assertThat(filesWithoutExtensions).containsExactlyInAnyOrderElementsOf(classes)
    }

    @Test
    fun `Ensure every yaml file has a schema class`() {
        val classes = memberMap.keys
        val filesWithoutExtensions = yamlFileData.keys.map { it.substringBeforeLast(".") }
        assertThat(classes).containsExactlyInAnyOrderElementsOf(filesWithoutExtensions)
    }

    @Test
    fun `Validate that all yaml topics have matching code value`() {
        yamlFileData.forEach { (fileName: String, topics: Map<String, Map<String, *>>) ->
            println("Testing: $fileName")
            val potentialClass = fileName.substringBeforeLast(".")
            val yamlTopicNames = topics["topics"]!!.toMap().map { it.value["name"] }
            val kotlinTopicNames = memberMap[potentialClass]
            assertThat(yamlTopicNames).containsExactlyInAnyOrderElementsOf(kotlinTopicNames)
        }
    }
}
