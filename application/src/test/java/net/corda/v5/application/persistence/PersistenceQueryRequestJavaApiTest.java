package net.corda.v5.application.persistence;

import net.corda.v5.application.persistence.query.NamedQueryFilter;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.mockito.Mockito.mock;

public class PersistenceQueryRequestJavaApiTest {

    final private NamedQueryFilter namedQueryFilter = mock(NamedQueryFilter.class);
    final private PersistenceQueryRequest persistenceQueryRequest =
            new PersistenceQueryRequest("test", Map.of("key", "value"), namedQueryFilter, "demo");
    final private PersistenceQueryRequest persistenceQueryRequestA =
            new PersistenceQueryRequest("test", Map.of("key", "value"), namedQueryFilter);
    final private PersistenceQueryRequest persistenceQueryRequestB =
            new PersistenceQueryRequest("test", Map.of("key", "value"));

    @Test
    public void getQueryName() {
        String result = persistenceQueryRequest.getQueryName();

        Assertions.assertThat(result).isNotNull();
        Assertions.assertThat(result).isEqualTo("test");
    }

    @Test
    public void getNamedParameters() {
        Map<String, Object> result = persistenceQueryRequest.getNamedParameters();

        Assertions.assertThat(result).isNotNull();
        Assertions.assertThat(result).isEqualTo(Map.of("key", "value"));
    }

    @Test
    public void getPostFilter() {
        NamedQueryFilter result = persistenceQueryRequest.getPostFilter();

        Assertions.assertThat(result).isNotNull();
        Assertions.assertThat(result).isEqualTo(namedQueryFilter);
    }

    @Test
    public void getPostProcessorName() {
        String result = persistenceQueryRequest.getPostProcessorName();

        Assertions.assertThat(result).isNotNull();
        Assertions.assertThat(result).isEqualTo("demo");
    }

    @Test
    public void build() {
        PersistenceQueryRequest result = new PersistenceQueryRequest.Builder("test", Map.of()).build();

        Assertions.assertThat(result).isNotNull();
        Assertions.assertThat(result.getQueryName()).isEqualTo("test");
        Assertions.assertThat(result.getNamedParameters()).isEqualTo(Map.of());
        Assertions.assertThat(result.getPostProcessorName()).isNull();
        Assertions.assertThat(result.getPostFilter()).isNull();
    }

    @Test
    public void withPostFilter() {
        PersistenceQueryRequest result =
                new PersistenceQueryRequest.Builder("test", Map.of()).withPostFilter(namedQueryFilter).build();

        Assertions.assertThat(result).isNotNull();
        Assertions.assertThat(result.getQueryName()).isEqualTo("test");
        Assertions.assertThat(result.getNamedParameters()).isEqualTo(Map.of());
        Assertions.assertThat(result.getPostProcessorName()).isNull();
        Assertions.assertThat(result.getPostFilter()).isEqualTo(namedQueryFilter);
    }

    @Test
    public void withPostProcessor() {
        PersistenceQueryRequest result = new PersistenceQueryRequest.Builder("test", Map.of()).withPostProcessor("demo").build();

        Assertions.assertThat(result).isNotNull();
        Assertions.assertThat(result.getQueryName()).isEqualTo("test");
        Assertions.assertThat(result.getNamedParameters()).isEqualTo(Map.of());
        Assertions.assertThat(result.getPostProcessorName()).isEqualTo("demo");
        Assertions.assertThat(result.getPostFilter()).isNull();
    }
}
