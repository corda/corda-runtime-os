package net.corda.schema.membership.provider;

import net.corda.schema.membership.MembershipSchema;
import net.corda.v5.base.versioning.Version;
import org.jetbrains.annotations.NotNull;

import java.io.InputStream;

/**
 * Provide the membership schema files to client code, ensuring the load works under OSGi and non-OSGi.
 * <p>
 * All provided InputStream objects should be closed by the client.
 */
public interface MembershipSchemaProvider {

    /**
     * Retrieve the schema file for a membership schema.
     * <p>
     * Note that this does not resolve $ref fields in the schema file.
     *
     * @param schema The membership schema to retrieve.
     * @param version The version of the membership schema to retrieve. See {@link MembershipSchema}.
     * @return An input stream of the resource file containing the schema.
     */
    @NotNull
    InputStream getSchema(@NotNull MembershipSchema schema, @NotNull Version version);

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
