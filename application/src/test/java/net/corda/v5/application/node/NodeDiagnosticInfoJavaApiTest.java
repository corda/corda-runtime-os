package net.corda.v5.application.node;

import net.corda.v5.application.cordapp.CordappInfo;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class NodeDiagnosticInfoJavaApiTest {

    private final NodeDiagnosticInfo nodeDiagnosticInfo = mock(NodeDiagnosticInfo.class);

    @Test
    public void getVersion() {
        String test = "test";
        when(nodeDiagnosticInfo.getVersion()).thenReturn(test);

        String result = nodeDiagnosticInfo.getVersion();

        Assertions.assertThat(result).isNotNull();
        Assertions.assertThat(result).isEqualTo(test);

        verify(nodeDiagnosticInfo, times(1)).getVersion();
    }

    @Test
    public void getRevision() {
        String test = "test";
        when(nodeDiagnosticInfo.getRevision()).thenReturn(test);

        String result = nodeDiagnosticInfo.getRevision();

        Assertions.assertThat(result).isNotNull();
        Assertions.assertThat(result).isEqualTo(test);

        verify(nodeDiagnosticInfo, times(1)).getRevision();
    }

    @Test
    public void getPlatformVersion() {
        int test = 5;
        when(nodeDiagnosticInfo.getPlatformVersion()).thenReturn(test);

        int result = nodeDiagnosticInfo.getPlatformVersion();

        Assertions.assertThat(result).isNotNull();
        Assertions.assertThat(result).isEqualTo(test);

        verify(nodeDiagnosticInfo, times(1)).getPlatformVersion();
    }

    @Test
    public void getVendor() {
        String test = "test";
        when(nodeDiagnosticInfo.getVendor()).thenReturn(test);

        String result = nodeDiagnosticInfo.getVendor();

        Assertions.assertThat(result).isNotNull();
        Assertions.assertThat(result).isEqualTo(test);

        verify(nodeDiagnosticInfo, times(1)).getVendor();
    }

    @Test
    public void getCordapps() {
        CordappInfo test = mock(CordappInfo.class);
        List<CordappInfo> testList = List.of(test);
        when(nodeDiagnosticInfo.getCordapps()).thenReturn(testList);

        List<CordappInfo> result = nodeDiagnosticInfo.getCordapps();

        Assertions.assertThat(result).isNotNull();
        Assertions.assertThat(result).isEqualTo(testList);

        verify(nodeDiagnosticInfo, times(1)).getCordapps();
    }
}
