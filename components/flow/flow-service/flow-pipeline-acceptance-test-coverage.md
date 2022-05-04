# Flow pipeline acceptance test coverage

The test coverage of the flow event pipeline's acceptance tests are documented here.

This document should be maintained so that we can ensure that we have quick visibility into our coverage as we are expecting a number of scenarios to be exercised.

## Sending
- (Send) (Send) Calling 'send' on initiated sessions sends a session data event and schedules a wakeup event ✅
- (Send) Calling 'send' on a closed session schedules an error event (not fully implemented, assert CLOSING, CLOSED, WAIT_FOR_FINAL_ACK states)
- (Send) Calling 'send' multiple times on initiated sessions resumes the flow and sends a session data events each time ✅

## Send + receiving

- (SendAndReceive) Calling 'sendAndReceive' on an initiated session sends a session data event
- (SendAndReceive) Calling 'sendAndReceive' on a closed session schedules an error event

## Receiving (can use parameterised tests to assert the same behaviour for sendAndReceive)

- (Receive) Receiving an out-of-order message does not resume the flow and sends a session ack ✅
- (Receive) Receiving a non-session-data event does not resume the flow and resends any unacknowledged events ✅ (Still requires the resends to be asserted)
- (Receive) Receiving all session data events for closing sessions resumes the flow and sends a session ack (not fully implemented)
- (Receive) Receiving a session data event for an unrelated session does not resume the flow and sends a session ack ✅
- (Receive) Receiving a session close event for an unrelated session does not resume the flow and sends a session ack ✅ (can be parameterised with the test above?)
- (Receive) Given two sessions receiving a single session data event does not resume the flow and sends a session ack ✅
- (Receive) Given two sessions receiving all session data events resumes the flow and sends session acks ✅
- (Receive) Given two sessions have already received their session data events when the flow calls 'receive' for both sessions at once the flow should schedule a wakeup event ✅
- (Receive) Given two sessions have already received their session data events when the flow calls 'receive' for each session individually the flow should schedule a wakeup event ✅
- (Receive) Given two sessions receiving a session close event for one resumes the flow with an error (not fully implemented, assert WAIT_FOR_FINAL_ACK session as well)
- (Receive) Given two sessions receiving a session data event for one and a close for another resumes the flow with an error
- (Any-non-receive-request-type) Receiving a session data event does not resume the flow and sends a session acks ✅

## Closing

- (CloseSessions) Given sessions have states of 'WAIT_FOR_FINAL_ACK' receiving all session acks resumes the flow (is this correct, not sure sequence of events to get here)
- (CloseSessions) Receiving an out-of-order message does not resume the flow and sends a session ack
- (CloseSessions) Calling 'close' on initiated sessions sends session close events
- (CloseSessions) Calling 'close' on an initiated and closed session sends a session close event to the initiated session
- (CloseSessions) Calling 'close' on locally closed sessions schedules a wakeup event to resume the flow
- (CloseSessions) Calling 'close' on sessions closed by peers sends session close events
- (CloseSessions) Receiving a single session close event does not resume the flow and sends a session ack
- (CloseSessions) Receiving all session close events resumes the flow and sends session acks
- (CloseSessions) Receiving a session close event for one session and a data for another resumes the flow with an error
- (CloseSessions) Receiving a wakeup event does not resume the flow and resends any unacknowledged events
- (CloseSessions) Given two sessions where one has already received a session close event calling close and then receiving a session close event for the other session resumes the flow and sends a session ack
- (CloseSessions) Given two sessions have already received their session close events calling 'close' for the sessions should schedule a wakeup event
- (Any-non-receive-request-type) Receiving session close events does not resume the flow and send session acks (can this be combined with the test above?)

## SubFlow session closing

- (SubFlowFinished) Given a subFlow contains only initiated sessions when the subFlow finishes session closes are sent
- (SubFlowFinished) Given a subFlow contains an initiated and closed session when the subFlow finishes a single session close is sent
- (SubFlowFinished) Given a subFlow contains only closed sessions when the subFlow finishes a wakeup event is scheduled to resume the flow

## Tests below are the same as (CloseSessions) but with a different request type

- (SubFlowFinished) Receiving a single session close event does not resume the flow and sends a session ack
- (SubFlowFinished) Receiving all session close events resumes the flow and sends session acks
- (SubFlowFinished) Receiving a session close event for one session and a data for another resumes the flow with an error
- (SubFlowFinished) Given two sessions where one has already received a session close event receiving a session close event for the other session resumes the flow and sends a session ack
- (SubFlowFinished) Given two sessions have already received their session close events when the flow calls 'close' for the sessions the flow should resume