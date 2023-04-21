package net.corda.kotlin;

import net.corda.kotlin.reflect.KotlinReflection;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.ArgumentsProvider;
import org.junit.jupiter.params.provider.ArgumentsSource;
import kotlin.reflect.KFunction;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static java.util.Collections.emptyList;
import static java.util.concurrent.TimeUnit.MINUTES;
import static kotlin.jvm.JvmClassMappingKt.getKotlinClass;
import static kotlin.reflect.full.KClasses.getDeclaredMemberFunctions;
import static kotlin.reflect.full.KClasses.getMemberFunctions;

@Timeout(value = 5, unit = MINUTES)
class JavaClassHierarchyTest {

    static class ClassProvider implements ArgumentsProvider {
        @Override
        public Stream<? extends Arguments> provideArguments(ExtensionContext context) {
            return Stream.of(
                Arguments.of(ImplA.class),
                Arguments.of(ImplB.class),
                Arguments.of(ImplC.class),
                Arguments.of(ImplD.class),
                Arguments.of(Api.class)
            );
        }
    }

    @ParameterizedTest(name = "{displayName} => {0}")
    @DisplayName("Declared Member Functions")
    @ArgumentsSource(ClassProvider.class)
    void testDeclaredMemberFunctions(Class<?> clazz) {
        Collection<KFunction<?>> kotlinFunctions = getDeclaredMemberFunctions(getKotlinClass(clazz));
        Collection<KFunction<?>> cordaFunctions = KotlinReflection.getKotlinClass(clazz).getDeclaredMemberFunctions();
        assertThat(cordaFunctions)
            .usingElementComparator(Helpers::compareKotlinFunctions)
            .containsExactlyInAnyOrderElementsOf(kotlinFunctions)
            .isNotEmpty();
    }

    @ParameterizedTest(name = "{displayName} => {0}")
    @DisplayName("Member Functions")
    @ArgumentsSource(ClassProvider.class)
    void testMemberFunctions(Class<?> clazz) {
        Collection<KFunction<?>> kotlinFunctions = getMemberFunctions(getKotlinClass(clazz));
        Collection<KFunction<?>> cordaFunctions = KotlinReflection.getKotlinClass(clazz).getMemberFunctions();
        assertThat(cordaFunctions)
            .usingElementComparator(Helpers::compareKotlinFunctions)
            .containsExactlyInAnyOrderElementsOf(kotlinFunctions)
            .isNotEmpty();
    }

    public static class ImplA {
        public Object getThingy() {
            return null;
        }
    }

    public static class ImplB extends ImplA {
        public List<?> getThingy() {
            return emptyList();
        }
    }

    public static class ImplC extends ImplB {
        @Override
        public ArrayList<String> getThingy() {
            return new ArrayList<>();
        }
    }

    // The KotlinClass comparator is by assignability
    // first, and fully qualified class name second.
    @SuppressWarnings("unused")
    interface ZApi {
        Object getThingy();
    }

    @SuppressWarnings("unused")
    public static class ImplD extends ImplC implements ZApi {
        public void somethingElse() {}
    }
}
