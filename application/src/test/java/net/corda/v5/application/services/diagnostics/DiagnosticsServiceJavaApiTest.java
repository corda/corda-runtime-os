package net.corda.v5.application.services.diagnostics;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class DiagnosticsServiceJavaApiTest {

    private final DiagnosticsService diagnosticsService = mock(DiagnosticsService.class);
    private final NodeVersionInfo nodeVersionInfo = mock(NodeVersionInfo.class);

    @Test
    public void getNodeVersionInfo() {
        when(diagnosticsService.getNodeVersionInfo()).thenReturn(nodeVersionInfo);

        NodeVersionInfo result = diagnosticsService.getNodeVersionInfo();

        Assertions.assertThat(result).isNotNull();
        Assertions.assertThat(result).isEqualTo(nodeVersionInfo);
    }
}
