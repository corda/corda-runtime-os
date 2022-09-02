package net.corda.v5.application.persistence;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.mockito.Mockito.*;

public class PersistenceServiceJavaApiTest {

    final private PersistenceService persistenceService = mock(PersistenceService.class);
    @SuppressWarnings("unchecked")
    List<Object> result = mock(List.class);

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
    public void findAll() {
        List<Integer> expectedResult = List.of(1, 2, 3, 4);
        PagedQuery<Integer> query = mock(PagedQuery.class);
        when(query.execute()).thenReturn(expectedResult);
        when(persistenceService.findAll(Integer.class)).thenReturn(query);

        List<Integer> result = persistenceService.findAll(Integer.class).execute();

        Assertions.assertThat(result).isNotNull();
        Assertions.assertThat(result).isEqualTo(expectedResult);
    }

    @Test
    public void query() {
        List<Integer> expectedResult = List.of(1, 2, 3, 4);
        ParameterisedQuery<Integer> query = mock(ParameterisedQuery.class);
        when(query.execute()).thenReturn(expectedResult);
        doReturn(query).when(persistenceService).query("test", Integer.class);

        List<Integer> result = persistenceService.query("test", Integer.class).execute();

        Assertions.assertThat(result).isNotNull();
        Assertions.assertThat(result).isEqualTo(expectedResult);
    }
}
