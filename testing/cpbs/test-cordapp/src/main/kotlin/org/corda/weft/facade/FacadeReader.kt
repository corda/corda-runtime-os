package org.corda.weft.facade

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import org.corda.weft.parameters.ParameterType
import org.corda.weft.parameters.TypedParameter
import java.io.InputStream
import java.io.Reader

/**
 * A [FacadeReader] reads a [Facade] from an input source.
 */
interface FacadeReader {

    /**
     * Read a [Facade] from the given [Reader].
     *
     * @param reader The reader to read from.
     */
    fun read(reader: Reader): Facade

    /**
     * Read a [Facade] from the given [InputStream].
     *
     * @param inputStream The input stream to read from.
     */
    fun read(input: InputStream): Facade = read(input.reader())

    /**
     * Read a [Facade] from the given [String].
     *
     * @param string The string to read from.
     */
    fun read(input: String): Facade = read(input.byteInputStream())

}

/**
 * Provides [FacadeReader]s from various formats. (This is a lie: it only does JSON).
 */
object FacadeReaders {

    /**
     * A [FacadeReader] that reads JSON.
     */
    @JvmStatic
    val JSON: FacadeReader
        get() = JacksonFacadeReader {
            ObjectMapper().registerKotlinModule().readValue(it, FacadeDefinition::class.java)
        }

}


/**
 * A [JacksonFacadeReader] reads a [Facade] from a JSON input source, using Jackson.
 *
 * @param mapper The [ObjectMapper] to use for reading the JSON.
 */
internal class JacksonFacadeReader(val deserialiser: (Reader) -> FacadeDefinition) : FacadeReader {

    override fun read(reader: Reader): Facade {
        val facadeJson = deserialiser(reader)

        val facadeId = FacadeId.of(facadeJson.id)
        val aliases = facadeJson.aliases?.mapValues { (_, v) -> ParameterType.of<Any>(v) }
            ?: emptyMap()

        val queries = facadeJson.queries?.map { (id, methodJson) ->
            parseFacadeMethod(facadeId, id, FacadeMethodType.QUERY, aliases, methodJson)
        } ?: emptyList()

        val commands = facadeJson.commands?.map { (id, methodJson) ->
            parseFacadeMethod(facadeId, id, FacadeMethodType.COMMAND, aliases, methodJson)
        } ?: emptyList()

        return Facade(
            facadeId,
            queries + commands
        )
    }

    private fun parseFacadeMethod(
        facadeId: FacadeId,
        id: String,
        methodType: FacadeMethodType,
        aliases: Map<String, ParameterType<Any>>,
        methodJson: FacadeMethodDefinition? // A method with neither in nor out parameters will have no methodJson
    ): FacadeMethod {
        val inParams = methodJson?.`in`
            ?.map { (name, type) -> TypedParameter(name, ParameterType.of(type, aliases)) }
            ?: emptyList()

        val outParams = methodJson?.out
            ?.map { (name, type) -> TypedParameter(name, ParameterType.of(type, aliases)) }
            ?: emptyList()

        return FacadeMethod(facadeId, id, methodType, inParams, outParams)
    }

}

internal data class FacadeDefinition(
    val id: String,
    val aliases: Map<String, String>?,
    val queries: Map<String, FacadeMethodDefinition?>?,
    val commands: Map<String, FacadeMethodDefinition?>?
)

internal data class FacadeMethodDefinition(val `in`: Map<String, String>?, val out: Map<String, String>?)
