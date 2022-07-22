import { memo, useEffect, useRef } from 'react';

import Message from '../Message/Message';
import { MessagePair } from '../Chat/Chat';
import style from '../Chat/chat.module.scss';

type Props = {
    counterParty: string | undefined;
    messages: Map<string, MessagePair>;
};

const Messages: React.FC<Props> = ({ counterParty, messages }) => {
    const messagesEndRef = useRef<HTMLDivElement>(null);

    useEffect(() => {
        if (!messagesEndRef.current) return;
        messagesEndRef.current.scrollIntoView();
    }, [messages]);

    return (
        <div className={style.messagesList}>
            {Array.from(messages).map(([key, value]) => {
                const isPendingMessage = value.direction === 'outgoing_pending';
                const isMyMessage = value.direction === 'outgoing' || isPendingMessage;
                return (
                    <Message
                        counterParty={counterParty ?? ''}
                        key={key}
                        message={value.content}
                        isMyMessage={isMyMessage}
                        isPending={isPendingMessage}
                        selectReplyParticipant={() => {}}
                    />
                );
            })}
            <div ref={messagesEndRef} />
        </div>
    );
};

export default memo(Messages, (prev, next) => {
    if (prev.counterParty !== next.counterParty) return false;

    if (prev.messages.size !== next.messages.size) return false;

    for (var [key, val] of prev.messages) {
        if (!prev.messages.has(key) || !next.messages.has(key)) {
            return false;
        }
        if (next.messages.get(key) !== next.messages.get(key)) {
            return false;
        }
    }

    if (JSON.stringify(prev.messages) !== JSON.stringify(next.messages)) return false;
    return true;
});
