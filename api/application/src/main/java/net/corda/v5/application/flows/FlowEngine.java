package net.corda.v5.application.flows;

import java.util.UUID;
import net.corda.v5.application.crypto.DigitalSignatureVerificationService;
import net.corda.v5.application.messaging.FlowSession;
import net.corda.v5.base.annotations.DoNotImplement;
import net.corda.v5.base.annotations.Suspendable;
import net.corda.v5.base.exceptions.CordaRuntimeException;
import net.corda.v5.base.types.MemberX500Name;
import org.jetbrains.annotations.NotNull;

/**
 * {@link FlowEngine} provides core flow related functionality.
 * <p>
 * Corda provides an instance of {@link DigitalSignatureVerificationService} to flows via property injection.
 */
@DoNotImplement
public interface FlowEngine {

    /**
     * Gets the flow id that identifies this flow.
     * <p>
     * A subFlow shares the same flow id as the flow that invoked it via {@link FlowEngine#subFlow}.
     */
    @NotNull
    UUID getFlowId();

    /**
     * Gets the {@link MemberX500Name} of the current virtual node executing the flow.
     */
    @NotNull
    MemberX500Name getVirtualNodeName();

    /**
     * Gets the context properties of the current flow.
     */
    @NotNull
    FlowContextProperties getFlowContextProperties();

    /**
     * Executes the given {@link SubFlow}.
     * <p>
     * This function returns once the {@link SubFlow} completes, returning either:
     * <ul><li>The result executing of {@link SubFlow#call}.</li>
     * <li>An exception thrown by {@link SubFlow#call}.</li></ul>
     *
     * Any open {@link FlowSession}s created within a {@link SubFlow} annotated with {@link InitiatingFlow} are sent:
     * <ul><li>Session close messages after successfully completing the {@link SubFlow}.</li>
     * <li>Session error messages when an exception is thrown from the {@link SubFlow}.</li></ul>
     *
     * @param subFlow The {@link SubFlow} to execute.
     * @param <R> The type returned by {@code subFlow}.
     *
     * @return The result of executing {@link SubFlow#call}.
     *
     * @throws CordaRuntimeException General type of exception thrown by most Corda APIs.
     *
     * @see SubFlow
     */
    @Suspendable
    <R> R subFlow(@NotNull SubFlow<R> subFlow);
}
