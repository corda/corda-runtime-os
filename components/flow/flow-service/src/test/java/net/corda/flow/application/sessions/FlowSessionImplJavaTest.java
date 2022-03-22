package net.corda.flow.application.sessions;

import net.corda.flow.application.sessions.FlowSessionImpl.State;
import net.corda.flow.fiber.FlowFiber;
import net.corda.flow.fiber.FlowFiberService;
import net.corda.flow.fiber.FlowIORequest;
import net.corda.v5.application.flows.FlowSession;
import net.corda.v5.base.types.MemberX500Name;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class FlowSessionImplJavaTest {

    private final FlowFiber flowFiber = mock(FlowFiber.class);
    private final FlowFiberService flowFiberService = mock(FlowFiberService.class);

    private final FlowSession session = new FlowSessionImpl(
        new MemberX500Name("Alice", "Alice Corp", "LDN", "GB"),
        "session id",
        flowFiberService,
        State.INITIATED
    );

    @BeforeEach
    public void beforeEach() {
        Map<String, String> received = new HashMap<>();
        received.put("session id", "hello there");
        when(flowFiber.suspend(any(FlowIORequest.SendAndReceive.class))).thenReturn(received);
        when(flowFiber.suspend(any(FlowIORequest.Receive.class))).thenReturn(received);
        when(flowFiberService.getExecutingFiber()).thenReturn(flowFiber);
    }

    @Test
    public void passingABoxedTypeToSendAndReceiveWillNotThrowAnException() {
        session.sendAndReceive(Integer.class, 1);
    }

    @Test
    public void passingAPrimitiveReceiveTypeToSendAndReceiveWillThrowAnException() {
        assertThrows(IllegalArgumentException.class, () -> session.sendAndReceive(int.class, 1));
    }

    @Test
    public void passingABoxedTypeToReceiveWillNotThrowAnException() {
        session.receive(Integer.class);
    }

    @Test
    public void passingAPrimitiveReceiveTypeToReceiveWillThrowAnException() {
        assertThrows(IllegalArgumentException.class, () -> session.receive(int.class));
    }
}
