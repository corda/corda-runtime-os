package net.corda.flow.application.sessions;

import net.corda.data.identity.HoldingIdentity;
import net.corda.flow.application.sessions.FlowSessionImpl.State;
import net.corda.flow.fiber.FlowFiber;
import net.corda.flow.fiber.FlowFiberExecutionContext;
import net.corda.flow.fiber.FlowFiberService;
import net.corda.flow.fiber.FlowIORequest;
import net.corda.flow.pipeline.sandbox.FlowSandboxContextTypes;
import net.corda.flow.pipeline.sandbox.SandboxDependencyInjector;
import net.corda.flow.state.FlowCheckpoint;
import net.corda.membership.read.MembershipGroupReader;
import net.corda.sandboxgroupcontext.SandboxGroupContext;
import net.corda.serialization.checkpoint.CheckpointSerializer;
import net.corda.v5.application.messaging.FlowSession;
import net.corda.v5.application.serialization.SerializationService;
import net.corda.v5.base.types.MemberX500Name;
import net.corda.v5.serialization.SerializedBytes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class FlowSessionImplJavaTest {

    private final SerializationService serializationService = mock(SerializationService.class);
    private final SandboxGroupContext sandboxGroupContext = mock(SandboxGroupContext.class);
    private final FlowFiberExecutionContext flowFiberExecutionContext = new FlowFiberExecutionContext(
            mock(SandboxDependencyInjector.class),
            mock(FlowCheckpoint.class),
            mock(CheckpointSerializer.class),
            sandboxGroupContext,
            new HoldingIdentity("CN=Bob, O=Bob Corp, L=LDN, C=GB", "group1"),
            mock(MembershipGroupReader.class)
    );
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
        Map<String, byte[]> received = new HashMap<>();
        received.put("session id", new byte[]{ 1, 2, 3 });
        when(serializationService.serialize(any())).thenReturn(new SerializedBytes(new byte[]{ 1, 2, 3 }));
        when(serializationService.deserialize(any(byte[].class), any())).thenReturn(1);
        when(sandboxGroupContext.get(FlowSandboxContextTypes.AMQP_P2P_SERIALIZATION_SERVICE, SerializationService.class))
                .thenReturn(serializationService);
        when(flowFiber.getExecutionContext()).thenReturn(flowFiberExecutionContext);
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
