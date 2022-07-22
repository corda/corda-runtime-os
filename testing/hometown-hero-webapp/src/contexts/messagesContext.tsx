import {
    FETCH_MESSAGE_FLOW_POLLING_DELETE_TIMEOUT,
    FETCH_MESSAGE_FLOW_POLLING_INTERVAL,
    MESSAGE_SEND_STATUS_POLL_INTERVAL_MS,
} from '@/constants/timeouts';
import React, { useCallback, useEffect, useState } from 'react';

import { Message } from '@/models/Message';
import { NotificationService } from '@r3/r3-tooling-design-system/exports';
import createCtx from './createCtx';
import { handleFlow } from '@/utils/flowHelpers';
import { useSessionStorage } from '@/hooks/useSessionStorage';
import useUserContext from './userContext';

export type UsersChatHistory = {
    username: string;
    chatHistories: ChatHistory[];
};

export type ChatHistory = {
    counterparty: string;
    messages: Message[];
};

export type Messages = {
    messages: Message[];
};

type MessagesContextProps = {
    chatHistories: ChatHistory[];
    getChatHistoryForCounterparty: (counterparty: string) => ChatHistory | undefined;
    getTotalMessagesForCounterparty: (counterparty: string) => number;
    fetchMessages: () => void;
};

const [useMessagesContext, Provider] = createCtx<MessagesContextProps>();
export default useMessagesContext;

export const MessagesContextProvider: React.FC<{ children?: React.ReactNode }> = ({ children }) => {
    const { username, password, holderShortId } = useUserContext();
    const [isPollingMessages, setIsPollingMessages] = useState<boolean>(false);
    const [chatHistories, setChatHistories] = useState<ChatHistory[]>([]);

    const getChatHistoryForCounterparty = useCallback(
        (counterparty: string): ChatHistory | undefined => {
            return chatHistories.find((chatHistory) => chatHistory.counterparty === counterparty);
        },
        [chatHistories]
    );

    const getTotalMessagesForCounterparty = useCallback(
        (sender: string): number => {
            const chatHistory = getChatHistoryForCounterparty(sender);
            if (!chatHistory) return 0;
            return chatHistory.messages.length;
        },
        [getChatHistoryForCounterparty]
    );

    const fetchMessages = useCallback(async () => {
        const clientRequestId = 'fetchMessage' + Date.now();
        const flowStatusInterval = await handleFlow({
            holderShortId,
            clientRequestId,
            flowType: 'net.cordapp.testing.chat.ChatReaderFlow',
            payload: JSON.stringify({}),
            pollIntervalMs: MESSAGE_SEND_STATUS_POLL_INTERVAL_MS,
            onStartFailure: (errorText) => {
                NotificationService.notify(
                    `Error starting flow with clientRequestId: ${clientRequestId}.. Error: ${errorText}`,
                    'error',
                    'danger'
                );
            },
            onStatusSuccess: (flowResult) => {
                const chatHistories = JSON.parse(flowResult) as ChatHistory[];
                setChatHistories(chatHistories);
            },
            onStatusFailure: (errorText) => {
                NotificationService.notify(
                    `Error polling flow status with clientRequestId: ${clientRequestId}.. Error: ${errorText}`,
                    'error',
                    'danger'
                );
            },
            auth: { username, password },
        });

        setTimeout(() => {
            clearInterval(flowStatusInterval);
        }, FETCH_MESSAGE_FLOW_POLLING_DELETE_TIMEOUT);
    }, []);

    useEffect(() => {
        if (holderShortId.length === 0 || isPollingMessages) return;
        setIsPollingMessages(true);

        const interval = setInterval(() => {
            fetchMessages();
        }, FETCH_MESSAGE_FLOW_POLLING_INTERVAL);

        return () => {
            clearInterval(interval);
        };
    }, [holderShortId]);

    return (
        <Provider
            value={{
                chatHistories,
                getChatHistoryForCounterparty,
                getTotalMessagesForCounterparty,
                fetchMessages,
            }}
        >
            {children}
        </Provider>
    );
};
