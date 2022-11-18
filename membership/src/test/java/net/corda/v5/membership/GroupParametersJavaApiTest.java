package net.corda.v5.membership;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class GroupParametersJavaApiTest {

    private final GroupParameters groupParameters = mock(GroupParameters.class);
    private final NotaryInfo notary = mock(NotaryInfo.class);

    @Test
    public void get() {
        final String key = "key";
        final String value = "value";
        when(groupParameters.get(key)).thenReturn(value);

        final Object obj = groupParameters.get(key);

        assertThat(obj).isNotNull();
        assertThat(obj).isEqualTo(value);
    }

    @Test
    public void getEntries() {
        Map<String, String> values = Map.of("key1", "value1", "key2", "value2");
        when(groupParameters.getEntries()).thenReturn(values.entrySet());

        final Set<Map.Entry<String, String>> keysTest = groupParameters.getEntries();

        assertThat(keysTest).isNotNull();
        assertThat(keysTest).isEqualTo(values.entrySet());
    }

    @Test
    public void minimumPlatformVersion() {
        when(groupParameters.getMinimumPlatformVersion()).thenReturn(5);
        final int platformVersion = groupParameters.getMinimumPlatformVersion();

        assertThat(platformVersion).isNotNull();
        assertThat(platformVersion).isEqualTo(5);
    }

    @Test
    public void modifiedTime() {
        final Instant instant = Instant.now();
        when(groupParameters.getModifiedTime()).thenReturn(instant);

        final Instant instantTest = groupParameters.getModifiedTime();

        assertThat(instantTest).isNotNull();
        assertThat(instantTest).isEqualTo(instant);
    }

    @Test
    public void epoch() {
        when(groupParameters.getEpoch()).thenReturn(669);

        final int epoch = groupParameters.getEpoch();

        assertThat(epoch).isNotNull();
        assertThat(epoch).isEqualTo(669);
    }

    @Test
    public void notaries() {
        when(groupParameters.getNotaries()).thenReturn(List.of(notary));

        final Collection<NotaryInfo> result = groupParameters.getNotaries();

        assertThat(result).containsExactly(notary);
    }
}
