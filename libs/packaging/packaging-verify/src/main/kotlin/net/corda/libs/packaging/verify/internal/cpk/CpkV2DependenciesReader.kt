package net.corda.libs.packaging.verify.internal.cpk

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import com.networknt.schema.JsonSchema
import com.networknt.schema.JsonSchemaFactory
import com.networknt.schema.SpecVersion
import net.corda.crypto.core.SecureHashImpl
import net.corda.libs.packaging.core.exception.DependencyMetadataException
import java.io.InputStream
import java.security.CodeSigner
import java.util.Base64

/**
 * CPK format version 2 CPKDependencies JSON file reader
 */
internal object CpkV2DependenciesReader {
    private const val CORDA_CPK_V2_SCHEMA = "corda-cpk-2.0.json"
    private val mapper = ObjectMapper()
    private val schema: JsonSchema = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V7).getSchema(
        this::class.java.classLoader.getResourceAsStream(CORDA_CPK_V2_SCHEMA)
            ?: throw IllegalStateException("Corda CPK v2 schema missing")
    )

    private class CPKDependencyFileV2(
        @JsonProperty("formatVersion")
        val formatVersion: String,
        @JsonProperty("dependencies")
        val dependencies: Array<Dependency>
    )

    /** Mapping for dependency object in CPKDependencies JSON file */
    private class Dependency(
        @JsonProperty("name")
        val name: String,
        @JsonProperty("version")
        val version: String,
        @JsonProperty("verifyFileHash")
        val verifyFileHash: VerifyFileHash?,
        @JsonProperty("verifySameSignerAsMe")
        val verifySameSignerAsMe: Boolean?
    )

    /** Mapping for verifyFileHash object in CPKDependencies JSON file */
    private class VerifyFileHash(
        @JsonProperty("algorithm")
        val algorithm: String,
        @JsonProperty("fileHash")
        val fileHash: String
    )

    /**
     * Reads CPK dependencies from [InputStream]
     * @param cpkName CPK's name used in re4porting errors
     * @param inputStream CPKDependencies [InputStream]
     * @param codeSigners CPK's code signers that will be used for "SameSignerAsMe" dependencies
     */
    fun readDependencies(
        cpkName: String,
        inputStream: InputStream,
        codeSigners: List<CodeSigner>
    ): List<CpkDependency> {
        try {
            // Validate against JSON Schema
            val node = mapper.readTree(inputStream)
            val errors = schema.validate(node)
            if (errors.isNotEmpty()) {
                val errorSet = errors.map { it.message }.toSet()
                throw DependencyMetadataException("CPK dependencies document validation error(s): $errorSet")
            }
            // Read document
            return mapper.readValue(mapper.treeAsTokens(node), object : TypeReference<CPKDependencyFileV2>() {})
                .dependencies
                .map{ toCpkDependency(it, codeSigners) }
        } catch (e: Exception) {
            throw DependencyMetadataException("Error reading CPK dependencies in CPK \"$cpkName\"", e)
        }
    }

    /**
     * Converts parsed [Dependency] to [CpkDependency]
     * @param dependency Dependency data specified in CPKDependencies
     * @param codeSigners CPK's code signers that will be used for "SameSignerAsMe" dependencies
     * @return [CpkDependency] created from data provided in CPKDependencies
     */
    private fun toCpkDependency(dependency: Dependency, codeSigners: List<CodeSigner>): CpkDependency {
        return if (dependency.verifyFileHash != null) {
            try {
                val hashData = Base64.getDecoder().decode(dependency.verifyFileHash.fileHash)
                val fileHash = SecureHashImpl(dependency.verifyFileHash.algorithm, hashData)
                CpkHashDependency(dependency.name, dependency.version, fileHash)
            } catch (e: IllegalArgumentException) {
                throw DependencyMetadataException("Error parsing CPK dependency: $dependency", e)
            }
        } else if (dependency.verifySameSignerAsMe != null && dependency.verifySameSignerAsMe) {
            CpkSignerDependency(dependency.name, dependency.version, codeSigners)
        } else {
            throw DependencyMetadataException("Error parsing CPK dependency: $dependency")
        }
    }
}