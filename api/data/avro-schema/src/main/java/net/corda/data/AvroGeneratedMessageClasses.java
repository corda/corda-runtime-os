package net.corda.data;

import org.apache.avro.specific.SpecificRecordBase;
import org.jetbrains.annotations.NotNull;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.LinkedHashSet;
import java.util.Set;

import static java.util.Collections.unmodifiableSet;

public final class AvroGeneratedMessageClasses {
    private static final String LOAD_FAILED_MESSAGE = "Unable to load generated Avro message classes.";
    private static final String RESOURCE_FILE = "generated-avro-message-classes.txt";

    private AvroGeneratedMessageClasses() {
    }

    @SuppressWarnings("unchecked")
    @NotNull
    public static Set<Class<? extends SpecificRecordBase>> getAvroGeneratedMessageClasses() {
        final Class<?> clazz = AvroGeneratedMessageClasses.class;

        final URL resource = clazz.getResource(RESOURCE_FILE);
        if (resource == null) {
            throw new SchemaLoadException(LOAD_FAILED_MESSAGE);
        }

        final ClassLoader loader = clazz.getClassLoader();
        final Set<Class<? extends SpecificRecordBase>> classNames = new LinkedHashSet<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(resource.openStream()))) {
            while (true) {
                final String className = reader.readLine();
                if (className == null) {
                    break;
                }
                classNames.add((Class<? extends SpecificRecordBase>) Class.forName(className, false, loader));
            }
        } catch (IOException | ClassNotFoundException e) {
            throw new SchemaLoadException(LOAD_FAILED_MESSAGE, e);
        }

        return unmodifiableSet(classNames);
    }
}
