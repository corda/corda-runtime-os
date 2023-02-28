# Flow pipeline acceptance test coverage

The test coverage of the flow event pipeline's acceptance tests are documented here.

This document should be maintained so that we can ensure that we have quick visibility into our coverage as we are expecting a number of scenarios to be exercised.

## General

- Receiving a non-session init event for a flow that does not exist discards the event ✅

## Wakeup

- Receiving a wakeup event for a flow that does not exist discards the event ✅
- Receiving a wakeup event for a flow that finished discards the event ✅
- Receiving a wakeup event for a flow that failed discards the event ✅

## Initiation

- Initiating a flow with a peer sends a session init event ✅
- Receiving a session ack resumes the initiating flow ✅
- Receiving a session init event starts an initiated flow and sends a session ack ✅
- Receiving a session init event for a flow that does not exist within the sandbox sends a session error event
- Receiving a session error event resumes the flow with an error ✅
- Open multiple sessions, receiving ack for only one session does not resume or set output events ✅

## Sending
- Calling 'send' on initiated sessions sends a session data event and schedules a wakeup event ✅
- Calling 'send' on a closed session schedules an error event (not fully implemented, assert CLOSING, CLOSED, WAIT_FOR_FINAL_ACK states)
- Calling 'send' multiple times on initiated sessions resumes the flow and sends a session data events each time ✅
- Given a flow resumes after receiving a session data event calling 'send' on the session sends a session data event and no session ack ✅
- Given a flow resumes after receiving session data events calling 'send' on the sessions sends session data events and no session ack for the session that resumed the flow ✅
- Calling 'send' on a session that is confirmed sets a wakeup event and data message ✅

## Send + receiving

- Calling 'sendAndReceive' on an initiated session sends a session data event ✅
- Calling 'sendAndReceive' on a closed session schedules an error event
- Given a flow resumes after receiving session data events calling 'sendAndReceive' on the sessions sends session data events and no session ack for the session that resumed the flow ✅

## Receiving

- Calling 'receive' on a closed session schedules a wakeup with error event
- Calling 'receive' on an errored session schedules a wakeup with error event
- Receiving an out-of-order session data events does not resume the flow and sends a session ack ✅
- Receiving a wakeup or session ack event does not resume the flow and resends any unacknowledged events ✅ (Still requires the resends to be asserted)
- Receiving all session data events for closing sessions resumes the flow and sends a session ack (not fully implemented)
- Receiving a session event for an unrelated session does not resume the flow and sends a session ack ✅
- Receiving a session close event instead of a data resumes the flow with an error ✅
- Given two sessions receiving a single session data event does not resume the flow and sends a session ack ✅
- Given two sessions receiving all session data events resumes the flow and sends session acks ✅
- Given two sessions where one has already received a session data event calling 'receive' and then receiving a session data event for the other session resumes the flow and sends a session ack ✅
- Given two sessions have already received their session data events when the flow calls 'receive' for both sessions at once the flow should schedule a wakeup event ✅
- Given two sessions have already received their session data events when the flow calls 'receive' for each session individually the flow should schedule a wakeup event ✅
- Given two sessions receiving a session close event for one resumes the flow with an error (not fully implemented, assert WAIT_FOR_FINAL_ACK session as well)
- Given two sessions receiving a session data event for one and a close for another resumes the flow with an error
- Given a non-receive request type receiving a session data event does not resume the flow and sends a session acks ✅
- Given two sessions receiving a single session error event does not resume the flow and schedules session cleanup ✅
- Given two sessions receiving a session data event for one and a session error event for the other resumes the flow with an error and schedules session cleanup ✅
- Given two sessions receiving a session error event first for one and a session data event for the other resumes the flow with an error and schedules session cleanup ✅
- Given two sessions receiving a session data event for one session and a session close event for the other resumes the flow with an error ✅
- Given two sessions receiving session close events for both sessions resumes the flow with an error ✅
- Given two sessions receiving a session data and then close event for one session and a session data event for the other resumes the flow  ✅
- Given a session, if it receives an out of order close and then an ordered data event, the flow resumes  ✅
  Complex messaging flow executing multiple sends and receives with 2 sessions, receives arrive non-sequentially ✅
## Closing

- Calling 'close' on initiated sessions sends session close events ✅
- Calling 'close' on an initiated and closing session sends session close events ✅
- Calling 'close' on an initiated and closed session sends a session close event to the initiated session ✅
- Calling 'close' on an initiated and errored session sends a session close event to the initiated session ✅
- Calling 'close' on a closed and errored session schedules a wakeup event and sends no session close events ✅
- Calling 'close' on errored sessions schedules a wakeup event and sends no session close events ✅
- Receiving an out-of-order session close events does not resume the flow and sends a session ack ✅
  Receiving an ordered session close event when waiting to receive data errors the flow ✅
- Receiving a wakeup or session ack event does not resume the flow and resends any unacknowledged events ✅ (Still requires the resends to be asserted)
- Receiving a session event for an unrelated session does not resume the flow and sends a session ack ✅
- Receiving a session data event instead of a close resumes the flow with an error ✅
- Given two sessions receiving a single session close event does not resume the flow sends a session ack and schedules session cleanup ✅
- Given two sessions receiving all session close events resumes the flow sends session acks and schedules session cleanup ✅
- Given two sessions where one enters WAIT_FOR_FINAL_ACK after calling 'close' resumes the flow after receiving a session ack and session close ✅ (test done twice but order switched around)
- Given two sessions have already received their session close events when the flow calls 'close' for both sessions at once the flow resumes after receiving session acks from each and schedules session cleanup ✅
- Given two sessions have already received their session close events when the flow calls 'close' for each session individually the flow resumes after receiving session acks respectively and schedules session cleanup ✅
- Given two closed sessions when the flow calls 'close' for both sessions a wakeup event is scheduled and no session close events are sent ✅
- Given a non-close request type receiving a session close event does not resume the flow and sends a session ack ✅
- Given a flow resumes after receiving session data events calling 'close' on the sessions sends session close events and no session ack for the session that resumed the flow ✅
- Given two sessions receiving a single session error event does not resume the flow and schedules session cleanup ✅
- Given two sessions receiving two session error events resumes the flow with an error and schedules session cleanup ✅
- Given two sessions receiving a session error event for one session and a session close event for the other resumes the flow with an error and schedules session cleanup ✅
- Given two sessions receiving a session data event for one session and a session close event for the other resumes the flow with an error and schedules session cleanup ✅
- Given two sessions receiving session data events for both sessions resumes the flow with an error and schedules session cleanup ✅

## SubFlow finishing

- Given a subFlow contains only initiated sessions when the subFlow finishes session close events are sent ✅
- Given a subFlow contains an initiated and closing session when the subFlow finishes session close events are sent ✅
- Given a subFlow contains an initiated and closed session when the subFlow finishes a single session close event is sent ✅
- Given a subFlow contains only closed sessions when the subFlow finishes a wakeup event is scheduled ✅
- Given a subFlow contains no sessions when the subFlow finishes a wakeup event is scheduled ✅
- Given a subFlow contains a closed and errored session when the subFlow finishes a wakeup event is scheduled and sends no session close events ✅
- Given a subFlow contains errored sessions when the subFlow finishes a wakeup event is scheduled and sends no session close events ✅
- Receiving an out-of-order session close events does not resume the flow and sends a session ack ✅
- Receiving a wakeup or session ack event does not resume the flow and resends any unacknowledged events ✅ (Still requires the resends to be asserted)
- Receiving a session close event for one session and a data for another resumes the flow with an error
- Receiving a session event for an unrelated session does not resume the flow and sends a session ack ✅
- Receiving a session data event instead of a close resumes the flow with an error ✅
- Given two sessions receiving a single session close event does not resume the flow sends a session ack and schedules session cleanup ✅
- Given two sessions receiving all session close events resumes the flow sends session acks and schedules session cleanup ✅
- Given two sessions where one enters WAIT_FOR_FINAL_ACK after calling 'close' resumes the flow after receiving a session ack and session close ✅ (test done twice but order switched around)
- Given two sessions receiving a single session error event does not resume the flow and schedules session cleanup ✅
- Given two sessions receiving two session error events resumes the flow with an error and schedules session cleanup ✅
- Given two sessions receiving a session error event for one session and a session close event for the other resumes the flow with an error and schedules session cleanup ✅
- Given an initiated top level flow with an initiated session when it finishes and calls SubFlowFinished a session close event is sent ✅
- Given two sessions receiving a session data event for one session and a session close event for the other resumes the flow with an error and schedules session cleanup ✅
- Given two sessions receiving session data events for both sessions resumes the flow with an error and schedules session cleanup ✅
- Given an initiated top level flow with a closed session when it finishes and calls SubFlowFinished a wakeup event is scheduled and does not send a session close event ✅
- Given an initiated top level flow with an errored session when it finishes and calls SubFlowFinished a wakeup event is scheduled and no session close event is sent ✅

## SubFlow failing

- Given a subFlow contains only initiated sessions when the subFlow fails a wakeup event is scheduled session error events are sent and session cleanup is scheduled ✅
- Given a subFlow contains an initiated and closed session when the subFlow fails a wakeup event is scheduled a single session error event is sent to the initiated session and session cleanup is scheduled ✅
- Given a subFlow contains only closed sessions when the subFlow fails a wakeup event is scheduled and no session error events are sent ✅
- Given a subFlow contains only errored sessions when the subFlow fails a wakeup event is scheduled and no session error events are sent ✅
- Given a subFlow contains no sessions when the subFlow fails a wakeup event is scheduled ✅
- Given an initiated top level flow with an initiated session when it finishes and calls SubFlowFailed a session error event is sent and session cleanup is scheduled ✅
- Given an initiated top level flow with a closed session when it finishes and calls SubFlowFailed a wakeup event is scheduled and does not send a session error event ✅
- Given an initiated top level flow with an errored session when it finishes and calls SubFlowFailed a wakeup event is scheduled and no session error event is sent` ✅

## Flow finishing

- A flow finishing removes the flow's checkpoint publishes a completed flow status and schedules flow cleanup ✅
- An initiated flow finishing removes the flow's checkpoint publishes a completed flow status and schedules flow cleanup ✅
- Given the flow has a WAIT_FOR_FINAL_ACK session receiving a session close event and then finishing the flow schedules flow and session cleanup ✅
- A flow finishing when previously in a retry state publishes a completed flow status and schedules flow cleanup ✅

## Flow failing

- A flow failing removes the flow's checkpoint publishes a failed flow status and schedules flow cleanup ✅
- An initiated flow failing removes the flow's checkpoint publishes a failed flow status and schedules flow cleanup ✅
- Given the flow has a WAIT_FOR_FINAL_ACK session receiving a session close event and then failing the flow schedules flow and session cleanup ✅

## External events

- Sending an external event sends a payload created by an external event factory ✅
- Receiving an external event response with the correct request id resumes the flow ✅
- Receiving an external event response with the wrong request id does not resume the flow and ignores the response ✅
- Given a flow has already received its external event response the flow can send another event and receive a response ✅
- Receiving an event does not resend the external event unless a 'transient' error is received ✅
- Receiving an event resends the external event if status is OK but the retry window has been surpassed ✅
- Receiving a 'transient' error response resends the external event if the retry window has been surpassed ✅
- Receiving a 'transient' error response does not resend the external event if the retry window has not been surpassed ✅
- Given a 'transient' error response has been received receiving an event will resend the external event if the retry window has been surpassed ✅
- Given a 'transient' error response has been received receiving a successful response resumes the flow and does not resend the event ✅
- Receiving a 'platform' error response resumes the flow with an error ✅
- Receiving a 'fatal' error response DLQs the flow and does not resume ✅