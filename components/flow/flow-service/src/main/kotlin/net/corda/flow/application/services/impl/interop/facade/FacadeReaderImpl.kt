package net.corda.flow.application.services.impl.interop.facade

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import net.corda.flow.application.services.impl.interop.parameters.TypeParameters
import net.corda.flow.application.services.impl.interop.parameters.TypedParameterImpl
import net.corda.v5.application.interop.facade.*
import java.io.Reader
import net.corda.v5.application.interop.parameters.ParameterType

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
class JacksonFacadeReader(val deserialiser: (Reader) -> FacadeDefinition) : FacadeReader {

    override fun read(reader: Reader): Facade {
        val facadeJson = deserialiser(reader)

        val facadeId = FacadeId.of(facadeJson.id)
        val aliases = facadeJson.aliases?.mapValues { (_, v) -> TypeParameters<Any>().of<Any>(v) }
            ?: emptyMap()

        val queries = facadeJson.queries?.map { (id, methodJson) ->
            parseFacadeMethod(facadeId, id, FacadeMethodType.QUERY, aliases, methodJson)
        } ?: emptyList()

        val commands = facadeJson.commands?.map { (id, methodJson) ->
            parseFacadeMethod(facadeId, id, FacadeMethodType.COMMAND, aliases, methodJson)
        } ?: emptyList()

        return FacadeImpl(
            facadeId,
            (queries + commands)
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
            ?.map { (name, type) -> TypedParameterImpl(name, TypeParameters<Any>().of(type, aliases)) }
            ?: emptyList()

        val outParams = methodJson?.out
            ?.map { (name, type) -> TypedParameterImpl(name, TypeParameters<Any>().of(type, aliases)) }
            ?: emptyList()

        return FacadeMethodImpl(facadeId, id, methodType, inParams, outParams)
    }

}

data class FacadeDefinition(
    val id: String,
    val aliases: Map<String, String>?,
    val queries: Map<String, FacadeMethodDefinition?>?,
    val commands: Map<String, FacadeMethodDefinition?>?
)

data class FacadeMethodDefinition(val `in`: Map<String, String>?, val out: Map<String, String>?)