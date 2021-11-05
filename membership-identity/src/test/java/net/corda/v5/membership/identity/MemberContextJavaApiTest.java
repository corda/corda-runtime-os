package net.corda.v5.membership.identity;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

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
        final Object object = new Object();
        when(memberContext.get(key)).thenReturn(object);

        final Object obj = memberContext.get(key);

        Assertions.assertThat(obj).isNotNull();
        Assertions.assertThat(obj).isEqualTo(object);
        verify(memberContext, times(1)).get(key);
    }

    @Test
    public void getKeys() {
        Set<String> keys = Set.of("key1", "key2");
        when(memberContext.getKeys()).thenReturn(keys);

        Set<String> keysTest = memberContext.getKeys();

        Assertions.assertThat(keysTest).isNotNull();
        Assertions.assertThat(keysTest).isEqualTo(keys);
        verify(memberContext, times(1)).getKeys();
    }
}
