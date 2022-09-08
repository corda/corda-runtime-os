package net.corda.osgi.framework;

import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.ArgumentsProvider;
import java.util.stream.Stream;

final class OSGiFrameworkTestArgumentsProvider implements ArgumentsProvider {

    @Override
    public Stream<? extends Arguments> provideArguments(ExtensionContext context) {
        return Stream.of(                                               // To enable Felix integration tests...
            // Arguments.of(OSGiFrameworkMain.FRAMEWORK_FACTORY_FQN),   // ... uncomment this line.
            Arguments.of(OSGiFrameworkFactoryMock.class.getName())
        );
    }
}
