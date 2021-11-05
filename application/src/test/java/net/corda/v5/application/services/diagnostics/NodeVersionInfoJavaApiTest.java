package net.corda.v5.application.services.diagnostics;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class NodeVersionInfoJavaApiTest {

    private final NodeVersionInfo nodeVersionInfo = mock(NodeVersionInfo.class);

    @Test
    public void getPlatformVersion() {
        when(nodeVersionInfo.getPlatformVersion()).thenReturn(1);

        int result = nodeVersionInfo.getPlatformVersion();

        Assertions.assertThat(result).isNotNull();
        Assertions.assertThat(result).isEqualTo(1);
    }

    @Test
    public void getReleaseVersion() {
        when(nodeVersionInfo.getReleaseVersion()).thenReturn("Test");

        String result = nodeVersionInfo.getReleaseVersion();

        Assertions.assertThat(result).isNotNull();
        Assertions.assertThat(result).isEqualTo("Test");
    }

    @Test
    public void getRevision() {
        when(nodeVersionInfo.getRevision()).thenReturn("Test");

        String result = nodeVersionInfo.getRevision();

        Assertions.assertThat(result).isNotNull();
        Assertions.assertThat(result).isEqualTo("Test");
    }

    @Test
    public void getVendor() {
        when(nodeVersionInfo.getVendor()).thenReturn("Test");

        String result = nodeVersionInfo.getVendor();

        Assertions.assertThat(result).isNotNull();
        Assertions.assertThat(result).isEqualTo("Test");
    }
}
