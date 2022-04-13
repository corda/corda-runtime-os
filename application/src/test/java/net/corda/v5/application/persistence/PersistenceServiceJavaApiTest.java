package net.corda.v5.application.persistence;

import net.corda.v5.application.persistence.query.NamedQueryFilter;
import net.corda.v5.base.stream.Cursor;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class PersistenceServiceJavaApiTest {

    final private PersistenceService persistenceService = mock(PersistenceService.class);
    @SuppressWarnings("unchecked")
    Cursor<Object> cursor = mock(Cursor.class);

    @Test
    public void persist() {
        persistenceService.persist(1);
        verify(persistenceService, times(1)).persist(1);
    }

    @Test
    public void persistList() {
        persistenceService.persist(List.of(1, 2, 3));
        verify(persistenceService, times(1)).persist(List.of(1, 2, 3));
    }

    @Test
    public void merge() {
        when(persistenceService.merge(1)).thenReturn(1);

        Integer result = persistenceService.merge(1);

        Assertions.assertThat(result).isNotNull();
        Assertions.assertThat(result).isEqualTo(1);
    }

    @Test
    public void mergeList() {
        when(persistenceService.merge(List.of(1, 2, 3, 4))).thenReturn(List.of(1, 2, 3, 4));

        List<Integer> result = persistenceService.merge(List.of(1, 2, 3, 4));

        Assertions.assertThat(result).isNotNull();
        Assertions.assertThat(result).isEqualTo(List.of(1, 2, 3, 4));
    }

    @Test
    public void remove() {
        persistenceService.remove(1);
        verify(persistenceService, times(1)).remove(1);
    }

    @Test
    public void removeList() {
        persistenceService.remove(List.of(1, 2, 3));
        verify(persistenceService, times(1)).remove(List.of(1, 2, 3));
    }

    @Test
    public void find() {
        when(persistenceService.find(Integer.class, 1)).thenReturn(1);

        Integer result = persistenceService.find(Integer.class, 1);

        Assertions.assertThat(result).isNotNull();
        Assertions.assertThat(result).isEqualTo(1);
    }

    @Test
    public void findList() {
        when(persistenceService.find(Integer.class, List.of(1, 2, 3, 4))).thenReturn(List.of(1, 2, 3, 4));

        List<Integer> result = persistenceService.find(Integer.class, List.of(1, 2, 3, 4));

        Assertions.assertThat(result).isNotNull();
        Assertions.assertThat(result).isEqualTo(List.of(1, 2, 3, 4));
    }

    @Test
    public void queryWithTwo() {
        when(persistenceService.query("test", Map.of("testKey", "testValue"))).thenReturn(cursor);

        Cursor<Object> result = persistenceService.query("test", Map.of("testKey", "testValue"));

        Assertions.assertThat(result).isNotNull();
        Assertions.assertThat(result).isEqualTo(cursor);
    }

    @Test
    public void queryWithThree() {
        NamedQueryFilter namedQueryFilter = mock(NamedQueryFilter.class);
        when(persistenceService.query("test", Map.of("testKey", "testValue"), namedQueryFilter)).thenReturn(cursor);

        Cursor<Object> result = persistenceService.query("test", Map.of("testKey", "testValue"), namedQueryFilter);

        Assertions.assertThat(result).isNotNull();
        Assertions.assertThat(result).isEqualTo(cursor);
    }

    @Test
    public void queryWithThreeParameters() {
        when(persistenceService.query("test", Map.of("testKey", "testValue"), "postProcessorName")).thenReturn(cursor);

        Cursor<Object> result = persistenceService.query("test", Map.of("testKey", "testValue"), "postProcessorName");

        Assertions.assertThat(result).isNotNull();
        Assertions.assertThat(result).isEqualTo(cursor);
    }

    @Test
    public void queryWithFour() {
        NamedQueryFilter namedQueryFilter = mock(NamedQueryFilter.class);
        when(persistenceService.query("test", Map.of("testKey", "testValue"), namedQueryFilter, "postProcessorName")).thenReturn(cursor);

        Cursor<Object> result = persistenceService.query("test", Map.of("testKey", "testValue"), namedQueryFilter, "postProcessorName");

        Assertions.assertThat(result).isNotNull();
        Assertions.assertThat(result).isEqualTo(cursor);
    }

    @Test
    public void queryWithPersistenceQueryRequest() {
        PersistenceQueryRequest persistenceQueryRequest = new PersistenceQueryRequest("test", Map.of("testKey", "testValue"), null, null);
        when(persistenceService.query(persistenceQueryRequest)).thenReturn(cursor);

        Cursor<Object> result = persistenceService.query(persistenceQueryRequest);

        Assertions.assertThat(result).isNotNull();
        Assertions.assertThat(result).isEqualTo(cursor);
    }

    @Test
    public void queryWithPersistenceQueryRequestBuilder() {
        PersistenceQueryRequest.Builder builder = new PersistenceQueryRequest.Builder("test", Map.of("testKey", "testValue'"));
        PersistenceQueryRequest persistenceQueryRequest = builder.build();
        when(persistenceService.query(persistenceQueryRequest)).thenReturn(cursor);

        Cursor<Object> result = persistenceService.query(persistenceQueryRequest);

        Assertions.assertThat(result).isNotNull();
        Assertions.assertThat(result).isEqualTo(cursor);
    }
}
