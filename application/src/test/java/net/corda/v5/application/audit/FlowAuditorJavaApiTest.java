package net.corda.v5.application.audit;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

public class FlowAuditorJavaApiTest {
    private final FlowAuditor flowAuditor = mock(FlowAuditor.class);

    @Test
    public void checkFlowPermission() {
        flowAuditor.checkFlowPermission("testPermissionName", Map.of("test", "test"));
        verify(flowAuditor, times(1)).checkFlowPermission("testPermissionName", Map.of("test", "test"));
    }

    @Test
    public void recordAuditEvent() {
        flowAuditor.recordAuditEvent("testEventType", "testComment", Map.of("test", "test"));
        verify(flowAuditor, times(1)).recordAuditEvent("testEventType", "testComment", Map.of("test", "test"));
    }
}
