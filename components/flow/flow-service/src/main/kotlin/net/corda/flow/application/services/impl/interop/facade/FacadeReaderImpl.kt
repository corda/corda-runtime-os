package net.corda.flow.application.services.impl.interop.facade

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.KotlinModule
import net.corda.common.json.serializers.standardTypesModule
import net.corda.flow.application.services.impl.interop.ProofOfActionSerialisationModule
import net.corda.flow.application.services.impl.interop.parameters.TypeParameters
import net.corda.flow.application.services.impl.interop.parameters.TypedParameterImpl
import net.corda.v5.application.interop.facade.Facade
import net.corda.v5.application.interop.facade.FacadeId
import net.corda.v5.application.interop.facade.FacadeMethod
import net.corda.v5.application.interop.facade.FacadeReader
import net.corda.v5.application.interop.parameters.ParameterType
import java.io.Reader
import java.util.*

/**
 * Provides [FacadeReader]s from various formats. (This is a lie: it only does JSON).
 */
object FacadeReaders {

    /**
     * A [FacadeReader] that reads JSON.
     */
    @JvmStatic
    val JSON: FacadeReader //TODO this is just for the spec - can be simplified
        get() = JacksonFacadeReader {
             ObjectMapper().apply {
                registerModule(KotlinModule.Builder().build())
                registerModule(JavaTimeModule())
                enable(DeserializationFeature.FAIL_ON_READING_DUP_TREE_KEY)
                setTimeZone(TimeZone.getTimeZone("UTC"))
                disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                registerModule(ProofOfActionSerialisationModule.module)
                registerModule(standardTypesModule())
            }.readValue(it, FacadeDefinition::class.java)
        }

}


/**
 * A [JacksonFacadeReader] reads a [Facade] from a JSON input source, using Jackson.
 * @param mapper The [ObjectMapper] to use for reading the JSON.
 */
class JacksonFacadeReader(val deserialiser: (Reader) -> FacadeDefinition) : FacadeReader {

    override fun read(reader: Reader): Facade {
        val facadeJson = deserialiser(reader)

        val facadeId = FacadeId.of(facadeJson.id)
        val aliases = facadeJson.aliases?.mapValues { (_, v) -> TypeParameters.of<Any>(v) }
            ?: emptyMap()

        val queries = facadeJson.queries?.map { (id, methodJson) ->
            parseFacadeMethod(facadeId, id, FacadeMethod.FacadeMethodType.QUERY, aliases, methodJson)
        } ?: emptyList()

        val commands = facadeJson.commands?.map { (id, methodJson) ->
            parseFacadeMethod(facadeId, id, FacadeMethod.FacadeMethodType.COMMAND, aliases, methodJson)
        } ?: emptyList()

        return FacadeImpl(
            facadeId,
            (queries + commands)
        )
    }

    private fun parseFacadeMethod(
        facadeId: FacadeId,
        id: String,
        methodType: FacadeMethod.FacadeMethodType,
        aliases: Map<String, ParameterType<Any>>,
        methodJson: FacadeMethodDefinition? // A method with neither in nor out parameters will have no methodJson
    ): FacadeMethod {
        val inParams = methodJson?.`in`
            ?.map { (name, type) -> TypedParameterImpl(name, TypeParameters.of(type, aliases)) }
            ?: emptyList()

        val outParams = methodJson?.out
            ?.map { (name, type) -> TypedParameterImpl(name, TypeParameters.of(type, aliases)) }
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