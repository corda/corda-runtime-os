package net.corda.v5.application.persistence;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.mockito.Mockito.*;

public class PagedQueryJavaApiTest {

    static class TestObject {
        public int foo;
    }

    final private PagedQuery<TestObject> query = mock(PagedQuery.class);

    @Test
    public void simpleNonPagedQuery() {
        List<TestObject> result = query.execute();
        verify(query, times(1)).execute();
    }

    @Test
    public void pagedQuery() {
        doReturn(query).when(query).setLimit(anyInt());
        doReturn(query).when(query).setOffset(anyInt());

        List<TestObject> result = query
                .setLimit(123)
                .setOffset(456)
                .execute();
        verify(query, times(1)).setLimit(123);
        verify(query, times(1)).setOffset(456);
        verify(query, times(1)).execute();
    }
}
