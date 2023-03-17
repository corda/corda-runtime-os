package net.corda.v5.application.messaging;

import net.corda.v5.application.flows.Flow;
import net.corda.v5.base.exceptions.CordaRuntimeException;
import net.corda.v5.base.annotations.Suspendable;
import org.jetbrains.annotations.NotNull;

/**
 * {@link ExternalMessaging} allows a flow to communicate with an external system via a predefined named channel.
 * <p>
 * The platform will provide an instance of {@link ExternalMessaging} to flows via property injection.
 * <p>
 * A {@link Flow} can send use this API to send messages to any external channel that has been defined as part of the
 * CorDapp ({@see <a href="">Defining External Messaging Channels</a>}).
 * <p>
 * Example usage:
 * <ul>
 * <li>Kotlin:<pre>{@code
 * class MyFlow : ClientStartableFlow {
 *    @CordaInject
 *    lateinit var externalMessaging: ExternalMessaging
 *
 *    override fun call(requestBody: RestRequestBody): String {
 *        val myChannelName = "my channel"
 *
 *        // Send a simple message to my channel
 *        externalMessaging.send(myChannelName, "hello")
 *
 *        // Send a simple message with an ID to my channel
 *        externalMessaging.send(myChannelName,"id-1", "hello")
 *
 *        return ""
 *    }
 *  }
 * }</pre></li>
 * <li>Java:<pre>{@code
 * class MyFlow implements ClientStartableFlow {
 *
 *    @CordaInject
 *    public ExternalMessaging externalMessaging;
 *
 *    @Override
 *    public String call(RestRequestBody requestBody) {
 *        String myChannelName = "my channel";
 *
 *        // Send a simple message to my channel
 *        externalMessaging.send(myChannelName, "hello");
 *
 *        // Send a simple message with an ID to my channel
 *        externalMessaging.send(myChannelName,"id-1", "hello");
 *
 *        return "";
 *    }
 * }
 * }</pre></li>
 * </ul>
 */
public interface ExternalMessaging {

    /**
     * Sends a message through a named channel.
     *
     * @param channelName The name of the channel the message should be sent through.
     * @param message   The contents of the message to be sent.
     * @throws CordaRuntimeException if the channel does not exist.
     */
    @Suspendable
    void send(@NotNull String channelName, @NotNull String message);

    /**
     * Sends a message with identifier through a named channel.
     *
     * @param channelName The name of the channel the message should be sent through.
     * @param messageId   An ID of the message to be sent.
     * @param message   The contents of the message to be sent.
     * @throws CordaRuntimeException if the channel does not exist.
     */
    @Suspendable
    void send(@NotNull String channelName, @NotNull String messageId, @NotNull String message);
}
