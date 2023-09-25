package net.corda.db.hsqldb.json;

import com.fasterxml.jackson.core.JacksonException;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.PrettyPrinter;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import com.fasterxml.jackson.databind.ObjectWriter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.SQLSyntaxErrorException;
import java.sql.Statement;
import java.util.Iterator;

public final class HsqldbJsonExtension {
    public static final String JSON_SQL_TYPE = "VARCHAR(32768)";
    public static final String NAME_SQL_TYPE = "VARCHAR(256)";
    private static final ObjectWriter WRITER;
    private static final ObjectReader READER;

    static {
        final ObjectMapper mapper = new ObjectMapper();
        WRITER = mapper.writer(new HsqldbPrettyPrinter());
        READER = mapper.reader();
    }

    public static boolean setup(@NotNull Connection connection) throws SQLException {
        try (final Statement statement = connection.createStatement()) {
            // Map our Java functions for parsing JSON data.
            statement.execute(
"CREATE FUNCTION JsonFieldAsText(IN json " + JSON_SQL_TYPE + ", IN fieldIndex INT)"
    + " RETURNS " + JSON_SQL_TYPE
    + " LANGUAGE JAVA DETERMINISTIC NO SQL"
    + " EXTERNAL NAME 'CLASSPATH:" + HsqldbJsonExtension.class.getName() + ".fieldByIndex'");

            statement.execute(
"CREATE FUNCTION JsonFieldAsText(IN json " + JSON_SQL_TYPE + ", IN fieldName " + NAME_SQL_TYPE + ')'
    + " RETURNS " + JSON_SQL_TYPE
    + " LANGUAGE JAVA DETERMINISTIC NO SQL"
    + " EXTERNAL NAME 'CLASSPATH:" + HsqldbJsonExtension.class.getName() + ".fieldByName'");

            statement.execute(
"CREATE FUNCTION JsonFieldAsObject(IN json " + JSON_SQL_TYPE + ", IN fieldIndex INT)"
    + " RETURNS " + JSON_SQL_TYPE
    + " LANGUAGE JAVA DETERMINISTIC NO SQL"
    + " EXTERNAL NAME 'CLASSPATH:" + HsqldbJsonExtension.class.getName() + ".objectByIndex'");

            statement.execute(
"CREATE FUNCTION JsonFieldAsObject(IN json " + JSON_SQL_TYPE + ", IN fieldName " + NAME_SQL_TYPE + ')'
    + " RETURNS " + JSON_SQL_TYPE
    + " LANGUAGE JAVA DETERMINISTIC NO SQL"
    + " EXTERNAL NAME 'CLASSPATH:" + HsqldbJsonExtension.class.getName() + ".objectByName'");

            statement.execute(
"CREATE FUNCTION HasJsonKey(IN json " + JSON_SQL_TYPE + ", IN keyName " + NAME_SQL_TYPE + ')'
    + " RETURNS BOOLEAN"
    + " LANGUAGE JAVA DETERMINISTIC NO SQL"
    + " EXTERNAL NAME 'CLASSPATH:" + HsqldbJsonExtension.class.getName() + ".hasKeyByName'");
        } catch (SQLSyntaxErrorException e) {
            // Most likely, these functions have already been created.
            return false;
        }
        return true;
    }

    @Nullable
    private static JsonNode readDocument(@NotNull String json) throws JacksonException {
        final JsonNode root = READER.readTree("{\"_\":" + json + '}');
        return root == null ? null : root.path("_");
    }

    @Nullable
    public static String fieldByName(@Nullable String json, @NotNull String fieldName) {
        if (json == null) {
            return null;
        }

        try {
            final JsonNode document = readDocument(json);
            if (document == null || !document.isObject()) {
                return null;
            }

            final JsonNode field = document.path(fieldName);
            return field.isValueNode() ? field.asText() : field.isMissingNode() ? null : WRITER.writeValueAsString(field);
        } catch (JacksonException e) {
            return null;
        }
    }

    @Nullable
    public static String fieldByIndex(@Nullable String json, int index) {
        if (json == null) {
            return null;
        }

        try {
            final JsonNode document = readDocument(json);
            if (document == null || !document.isArray()) {
                return null;
            }

            final JsonNode field = document.path(index);
            return field.isValueNode() ? field.asText() : field.isMissingNode() ? null : WRITER.writeValueAsString(field);
        } catch (JacksonException e) {
            return null;
        }
    }

    @Nullable
    public static String objectByName(@Nullable String json, @NotNull String fieldName) {
        if (json == null) {
            return null;
        }

        try {
            final JsonNode document = readDocument(json);
            if (document == null || !document.isObject()) {
                return null;
            }

            final JsonNode field = document.path(fieldName);
            return field.isMissingNode() ? null : WRITER.writeValueAsString(field);
        } catch (JacksonException e) {
            return null;
        }
    }

    @Nullable
    public static String objectByIndex(@Nullable String json, int index) {
        if (json == null) {
            return null;
        }

        try {
            final JsonNode document = readDocument(json);
            if (document == null || !document.isArray()) {
                return null;
            }

            final JsonNode field = document.path(index);
            return field.isMissingNode() ? null : WRITER.writeValueAsString(document.path(index));
        } catch (JacksonException e) {
            return null;
        }
    }

    private static boolean hasMatch(@NotNull JsonNode arrayNode, @NotNull String keyName) {
        final Iterator<JsonNode> iterator = arrayNode.elements();
        while (iterator.hasNext()) {
            final JsonNode element = iterator.next();
            if ((element.isValueNode() && keyName.equals(element.asText()))
                || !element.path(keyName).isMissingNode()) {
                return true;
            }
        }
        return false;
    }

    @Nullable
    public static Boolean hasKeyByName(@Nullable String json, @NotNull String keyName) {
        if (json == null) {
            return null;
        }

        try {
            final JsonNode document = readDocument(json);
            return document != null && (
                (document.isObject() && !document.path(keyName).isMissingNode()) ||
                (document.isArray() && hasMatch(document, keyName))
            );
        } catch (JacksonException e) {
            return false;
        }
    }

    /**
     * Formatting rules for writing our {@link JsonNode} objects.
     */
    private static final class HsqldbPrettyPrinter implements PrettyPrinter {
        @Override
        public void writeRootValueSeparator(JsonGenerator gen) {
        }

        @Override
        public void writeStartObject(@NotNull JsonGenerator gen) throws IOException {
            gen.writeRaw('{');
        }

        @Override
        public void writeEndObject(@NotNull JsonGenerator gen, int nrOfEntries) throws IOException {
            gen.writeRaw('}');
        }

        @Override
        public void writeObjectEntrySeparator(@NotNull JsonGenerator gen) throws IOException {
            gen.writeRaw(", ");
        }

        @Override
        public void writeObjectFieldValueSeparator(@NotNull JsonGenerator gen) throws IOException {
            gen.writeRaw(": ");
        }

        @Override
        public void writeStartArray(@NotNull JsonGenerator gen) throws IOException {
            gen.writeRaw('[');
        }

        @Override
        public void writeEndArray(@NotNull JsonGenerator gen, int nrOfValues) throws IOException {
            gen.writeRaw(']');
        }

        @Override
        public void writeArrayValueSeparator(@NotNull JsonGenerator gen) throws IOException {
            gen.writeRaw(", ");
        }

        @Override
        public void beforeArrayValues(JsonGenerator gen) {
        }

        @Override
        public void beforeObjectEntries(JsonGenerator gen) {
        }
    }
}
