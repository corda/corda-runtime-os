package net.corda.v5.membership;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;
import java.util.Set;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class GroupParametersJavaApiTest {

    private final GroupParameters groupParameters = mock(GroupParameters.class);

    @Test
    public void get() {
        final String key = "key";
        final String value = "value";
        when(groupParameters.get(key)).thenReturn(value);

        final Object obj = groupParameters.get(key);

        Assertions.assertThat(obj).isNotNull();
        Assertions.assertThat(obj).isEqualTo(value);
    }

    @Test
    public void getEntries() {
        Map<String, String> values = Map.of("key1", "value1", "key2", "value2");
        when(groupParameters.getEntries()).thenReturn(values.entrySet());

        final Set<Map.Entry<String, String>> keysTest = groupParameters.getEntries();

        Assertions.assertThat(keysTest).isNotNull();
        Assertions.assertThat(keysTest).isEqualTo(values.entrySet());
    }

    @Test
    public void minimumPlatformVersion() {
        when(groupParameters.getMinimumPlatformVersion()).thenReturn(5);
        final int platformVersion = groupParameters.getMinimumPlatformVersion();

        Assertions.assertThat(platformVersion).isNotNull();
        Assertions.assertThat(platformVersion).isEqualTo(5);
    }

    @Test
    public void modifiedTime() {
        final Instant instant = Instant.now();
        when(groupParameters.getModifiedTime()).thenReturn(instant);

        final Instant instantTest = groupParameters.getModifiedTime();

        Assertions.assertThat(instantTest).isNotNull();
        Assertions.assertThat(instantTest).isEqualTo(instant);
    }

    @Test
    public void epoch() {
        when(groupParameters.getEpoch()).thenReturn(669);

        final int epoch = groupParameters.getEpoch();

        Assertions.assertThat(epoch).isNotNull();
        Assertions.assertThat(epoch).isEqualTo(669);
    }
}
