package net.corda.schema.configuration.provider

import net.corda.v5.base.versioning.Version
import java.io.InputStream

/**
 * Provide the configuration schema files to client code, ensuring the load works under OSGi and non-OSGi.
 *
 * All provided InputStream objects should be closed by the client.
 */
interface SchemaProvider {

    /**
     * Retrieve the schema file for a top-level configuration key.
     *
     * Note that this does not resolve $ref fields in the schema file. However, these references should point to another
     * file contained in this module, which can then be retrieved with [getSchemaFile].
     *
     * @param key The top-level configuration key to retrieve schema for. See ConfigKeys.
     * @return An input stream of the resource file containing the schema.
     */
    fun getSchema(key: String, version: Version): InputStream

    /**
     * Retrieve a schema file with the given path.
     *
     * This can be used to retrieve files required to resolve $ref fields in the schema.
     *
     * @param fileName The file to retrieve. Should be a path from the root of the resources.
     * @return An input stream of the resource file containing the schema.
     */
    fun getSchemaFile(fileName: String): InputStream
}