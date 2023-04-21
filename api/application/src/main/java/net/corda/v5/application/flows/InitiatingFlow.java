package net.corda.v5.application.flows;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * {@link InitiatingFlow} marks a flow as initiating, meaning that it starts a {@link ResponderFlow} when creating a session with
 * another network entity.
 * <p>
 * Any flows that participate in flow sessions must declare a protocol name, using {@link InitiatingFlow#protocol}
 * and {@link InitiatedBy#protocol}. The platform will use the protocol name to establish what {@link ResponderFlow} to invoke on
 * the responder side when the initiator side creates a session.
 * <p>
 * For example, to set up an initiator-responder pair, declare the following:
 * <pre>{@code
 * @InitiatingFlow(protocol = "myprotocol")
 * class MyFlowInitiator : Flow {
 *  ...
 * }
 *
 * @InitiatedBy(protocol = "myprotocol")
 * class MyFlowResponder : ResponderFlow {
 *  ...
 * }
 * }</pre>
 *
 * Flows may also optionally declare a range of protocol versions they support. By default, flows support protocol
 * version 1 only. When initiating a flow, the platform will look for the highest supported protocol version as declared
 * on the initiating side and start that flow on the responder side.
 *
 * @see InitiatedBy
 * @see ResponderFlow
 */
@Target(TYPE)
@Documented
@Retention(RUNTIME)
public @interface InitiatingFlow {
    /**
     * @return The protocol of the annotated flow.
     */
    String protocol();

    /**
     * @return The protocol version.
     */
    int[] version() default { 1 };
}
