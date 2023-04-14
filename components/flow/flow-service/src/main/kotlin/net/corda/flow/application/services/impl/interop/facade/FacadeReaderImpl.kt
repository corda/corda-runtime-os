package net.corda.flow.application.services.impl.interop.facade

import com.fasterxml.jackson.databind.ObjectMapper
import net.corda.v5.application.interop.facade.*
import java.io.Reader
import net.corda.v5.application.interop.parameters.ParameterType

/**
 * A [JacksonFacadeReader] reads a [Facade] from a JSON input source, using Jackson.
 *
 * @param mapper The [ObjectMapper] to use for reading the JSON.
 */
class JacksonFacadeReader(val deserialiser: (Reader) -> FacadeDefinition) : FacadeReader {

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
            ?.map { (name, type) -> ParameterType(name, ParameterType.of(type, aliases)) }
            ?: emptyList()

        val outParams = methodJson?.out
            ?.map { (name, type) -> ParameterType(name, ParameterType.of(type, aliases)) }
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