package net.corda.flow.application.sessions;

import co.paralleluniverse.fibers.FiberScheduler;
import net.corda.flow.application.serialization.SerializationServiceInternal;
import net.corda.flow.fiber.FlowContinuation;
import net.corda.flow.fiber.FlowFiber;
import net.corda.flow.fiber.FlowFiberExecutionContext;
import net.corda.flow.fiber.FlowFiberService;
import net.corda.flow.fiber.FlowIORequest;
import net.corda.flow.fiber.FlowLogicAndArgs;
import net.corda.flow.pipeline.sandbox.FlowSandboxGroupContext;
import net.corda.flow.pipeline.sandbox.SandboxDependencyInjector;
import net.corda.flow.state.FlowCheckpoint;
import net.corda.flow.state.FlowContext;
import net.corda.membership.read.MembershipGroupReader;
import net.corda.sandboxgroupcontext.CurrentSandboxGroupContext;
import net.corda.serialization.checkpoint.CheckpointSerializer;
import net.corda.v5.application.messaging.FlowSession;
import net.corda.v5.base.types.MemberX500Name;
import net.corda.v5.serialization.SerializedBytes;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Future;

import static net.corda.test.util.identity.IdentityUtilsKt.createTestHoldingIdentity;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class FlowSessionImplJavaTest {

    private final FlowSandboxGroupContext flowSandboxGroupContext = mock(FlowSandboxGroupContext.class);
    private final SerializationServiceInternal serializationService = mock(SerializationServiceInternal.class);
    private final SandboxDependencyInjector sandboxDependencyInjector = mock(SandboxDependencyInjector.class);
    private final CheckpointSerializer checkpointSerializer = mock(CheckpointSerializer.class);
    private final FlowFiberExecutionContext flowFiberExecutionContext = new FlowFiberExecutionContext(
            mock(FlowCheckpoint.class),
            flowSandboxGroupContext,
            createTestHoldingIdentity("CN=Bob, O=Bob Corp, L=LDN, C=GB", "group1"),
            mock(MembershipGroupReader.class),
            mock(CurrentSandboxGroupContext.class),
            Map.of()
    );
    private final FlowFiber flowFiber = new FakeFiber(flowFiberExecutionContext);
    private final FlowFiberService flowFiberService = mock(FlowFiberService.class);
    private final FlowContext flowContext = mock(FlowContext.class);

    private final FlowSession session = new FlowSessionImpl(
            new MemberX500Name("Alice", "Alice Corp", "LDN", "GB"),
            "session id",
            flowFiberService,
            serializationService,
            flowContext,
            FlowSessionImpl.Direction.INITIATED_SIDE
    );

    private static class FakeFiber implements FlowFiber {
        @NotNull
        @Override
        public UUID getFlowId() {
            return null;
        }

        private FlowFiberExecutionContext fiberContext;

        public FakeFiber(FlowFiberExecutionContext context) {
            fiberContext = context;
        }

        @NotNull
        @Override
        public FlowLogicAndArgs getFlowLogic() {
            return null;
        }

        @Override
        public <SUSPENDRETURN> SUSPENDRETURN suspend(FlowIORequest<? extends SUSPENDRETURN> flowIORequest) {
            Map<String, byte[]> received = new HashMap<>();
            received.put("session id", new byte[]{1, 2, 3});
            return (SUSPENDRETURN) received;
        }

        @NotNull
        @Override
        public FlowFiberExecutionContext getExecutionContext() {
            return fiberContext;
        }

        @NotNull
        @Override
        public Future<FlowIORequest<?>> startFlow(@NotNull FlowFiberExecutionContext flowFiberExecutionContext) {
            return null;
        }

        @NotNull
        @Override
        public Future<FlowIORequest<?>> resume(@NotNull FlowFiberExecutionContext flowFiberExecutionContext, @NotNull FlowContinuation suspensionOutcome, @NotNull FiberScheduler scheduler) {
            return null;
        }
    }

    @BeforeEach
    public void beforeEach() {
        Map<String, byte[]> received = new HashMap<>();
        received.put("session id", new byte[]{1, 2, 3});
        when(serializationService.serialize(any())).thenReturn(new SerializedBytes(new byte[]{1, 2, 3}));
        when(serializationService.deserializeAndCheckType(any(byte[].class), any())).thenReturn(1);
        when(flowSandboxGroupContext.getDependencyInjector()).thenReturn(sandboxDependencyInjector);
        when(flowSandboxGroupContext.getCheckpointSerializer()).thenReturn(checkpointSerializer);
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
