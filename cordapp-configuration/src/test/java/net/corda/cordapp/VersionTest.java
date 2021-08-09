package net.corda.cordapp;

import org.gradle.api.tasks.StopExecutionException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.ArgumentsProvider;
import org.junit.jupiter.params.provider.ArgumentsSource;

import java.util.stream.Stream;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Timeout(value = 30, unit = SECONDS)
class VersionTest {
    static class LessThanProvider implements ArgumentsProvider {
        @Override
        public Stream<? extends Arguments> provideArguments(ExtensionContext context) {
            return Stream.of(
                Arguments.of("6.0", "6.0.1"),
                Arguments.of("6.0.0", "6.0.1"),
                Arguments.of("6.0.0", "6.1"),
                Arguments.of("6.0.0", "6.1-snapshot"),
                Arguments.of("6.0.0", "6.1.0"),
                Arguments.of("6.0.0", "6.1.1"),
                Arguments.of("6.0.0", "6.1.2"),
                Arguments.of("6.0.0", "6.1.2-SNAPSHOT")
            );
        }
    }

    @ParameterizedTest(name = "{displayName} -> {0} < {1}")
    @DisplayName("Less Than")
    @ArgumentsSource(LessThanProvider.class)
    void testLessThan(String lesser, String greater) {
        assertTrue(Version.parse(lesser).compareTo(Version.parse(greater)) < 0);
    }

    static class EqualsProvider implements ArgumentsProvider {
        @Override
        public Stream<? extends Arguments> provideArguments(ExtensionContext context) {
            return Stream.of(
                Arguments.of("6.0", "6.0.0"),
                Arguments.of("6.0-SNAPSHOT", "6.0.0-SNAPSHOT"),
                Arguments.of("6.0.0-snapshot", "6.0.0-SNAPSHOT")
            );
        }
    }

    @ParameterizedTest(name = "{displayName} -> {0} = {1}")
    @DisplayName("Equals")
    @ArgumentsSource(EqualsProvider.class)
    void testEquals(String first, String second) {
        assertEquals(Version.parse(first), Version.parse(second));
    }

    static class BaseVersionProvider implements ArgumentsProvider {
        @Override
        public Stream<? extends Arguments> provideArguments(ExtensionContext context) {
            return Stream.of(
                Arguments.of("6.0", "6.0.0"),
                Arguments.of("6.0.0", "6.0.0"),
                Arguments.of("6.0-SNAPSHOT", "6.0.0"),
                Arguments.of("6.1.2-SNAPSHOT", "6.1.2"),
                Arguments.of("6.1.2-DevPreview-RC01", "6.1.2")
            );
        }
    }

    @ParameterizedTest(name = "{displayName} -> {0} has base {1}")
    @DisplayName("Base Version")
    @ArgumentsSource(BaseVersionProvider.class)
    void testBaseVersion(String version, String baseVersion) {
        assertEquals(baseVersion, Version.parse(version).getBaseVersion().toString());
    }

    static class BadProvider implements ArgumentsProvider {
        @Override
        public Stream<? extends Arguments> provideArguments(ExtensionContext context) {
            return Stream.of(
                Arguments.of("6"),
                Arguments.of("6.0-"),
                Arguments.of("6.0.0.0")
            );
        }
    }

    @ParameterizedTest(name = "{displayName} -> {0}")
    @DisplayName("BadVersion")
    @ArgumentsSource(BadProvider.class)
    void testBadVersion(String bad) {
        assertThrows(StopExecutionException.class, () -> Version.parse(bad));
    }
}
