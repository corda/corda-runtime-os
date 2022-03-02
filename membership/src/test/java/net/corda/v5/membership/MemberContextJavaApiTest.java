package net.corda.v5.membership;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class MemberContextJavaApiTest {

    private final MemberContext memberContext = mock(MemberContext.class);

    @Test
    public void get() {
        final String key = "key";
        final String value = "value";
        when(memberContext.get(key)).thenReturn(value);

        final Object obj = memberContext.get(key);

        Assertions.assertThat(obj).isNotNull();
        Assertions.assertThat(obj).isEqualTo(value);
        verify(memberContext, times(1)).get(key);
    }

    @Test
    public void getKeys() {
        Set<Map.Entry<String, String>> entries = Map.of("key", "value").entrySet();
        when(memberContext.getEntries()).thenReturn(entries);

        Set<Map.Entry<String, String>> entriesTest = memberContext.getEntries();

        Assertions.assertThat(entriesTest).isNotNull();
        Assertions.assertThat(entriesTest).isEqualTo(entries);
        verify(memberContext, times(1)).getEntries();
    }
}
