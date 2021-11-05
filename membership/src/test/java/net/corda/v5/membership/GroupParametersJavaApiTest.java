package net.corda.v5.membership;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Set;

import static net.corda.v5.membership.GroupParameters.EPOCH_KEY;
import static net.corda.v5.membership.GroupParameters.MINIMUM_PLATFORM_VERSION_KEY;
import static net.corda.v5.membership.GroupParameters.MODIFIED_TIME_KEY;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class GroupParametersJavaApiTest {

    private final GroupParameters groupParameters = mock(GroupParameters.class);

    @Test
    public void get() {
        final String key = "key";
        final Object object = new Object();
        when(groupParameters.get(key)).thenReturn(object);

        final Object obj = groupParameters.get(key);

        Assertions.assertThat(obj).isNotNull();
        Assertions.assertThat(obj).isEqualTo(object);
    }

    @Test
    public void getKeys() {
        Set<String> keys = Set.of("key1", "key2");
        when(groupParameters.getKeys()).thenReturn(keys);

        final Set<String> keysTest = groupParameters.getKeys();

        Assertions.assertThat(keysTest).isNotNull();
        Assertions.assertThat(keysTest).isEqualTo(keys);
    }

    @Test
    public void minimumPlatformVersion() {
        when(groupParameters.get(MINIMUM_PLATFORM_VERSION_KEY)).thenReturn(5);
        final int platformVersion = GroupParameters.getMinimumPlatformVersion(groupParameters);

        Assertions.assertThat(platformVersion).isNotNull();
        Assertions.assertThat(platformVersion).isEqualTo(5);
    }

    @Test
    public void modifiedTime() {
        final Instant instant = Instant.now();
        when(groupParameters.get(MODIFIED_TIME_KEY)).thenReturn(instant);

        final Instant instantTest = GroupParameters.getModifiedTime(groupParameters);

        Assertions.assertThat(instantTest).isNotNull();
        Assertions.assertThat(instantTest).isEqualTo(instant);
    }

    @Test
    public void epoch() {
        when(groupParameters.get(EPOCH_KEY)).thenReturn(669);

        final int epoch = GroupParameters.getEpoch(groupParameters);

        Assertions.assertThat(epoch).isNotNull();
        Assertions.assertThat(epoch).isEqualTo(669);
    }
}
