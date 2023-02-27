package net.corda.schema.configuration.provider;

import net.corda.v5.base.versioning.Version;
import org.jetbrains.annotations.NotNull;

import java.io.InputStream;

/**
 * Provide the configuration schema files to client code, ensuring the load works under OSGi and non-OSGi.
 * <p>
 * All provided InputStream objects should be closed by the client.
 */
public interface SchemaProvider {

    /**
     * Retrieve the schema file for a top-level configuration key.
     * <p>
     * Note that this does not resolve $ref fields in the schema file. However, these references should point to another
     * file contained in this module, which can then be retrieved with {@link #getSchemaFile}.
     *
     * @param key The top-level configuration key to retrieve schema for. See ConfigKeys.
     * @return An input stream of the resource file containing the schema.
     */
    @NotNull
    InputStream getSchema(@NotNull String key, @NotNull Version version);

    /**
     * Retrieve a schema file with the given path.
     * <p>
     * This can be used to retrieve files required to resolve $ref fields in the schema.
     *
     * @param fileName The file to retrieve. Should be a path from the root of the resources.
     * @return An input stream of the resource file containing the schema.
     */
    @NotNull
    InputStream getSchemaFile(@NotNull String fileName);
}
