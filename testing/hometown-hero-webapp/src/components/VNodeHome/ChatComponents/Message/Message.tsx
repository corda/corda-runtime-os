import { useMemo, useState } from 'react';

import { IconButton } from '@r3/r3-tooling-design-system/exports';
import { Message as MessageType } from '@/models/Message';
import style from './message.module.scss';

type Props = {
    message: MessageType;
    isMyMessage: boolean;
    selectReplyParticipant: (participant: string) => void;
};

const Message: React.FC<Props> = ({ message, isMyMessage, selectReplyParticipant }) => {
    const [isHoveringOnParticipant, setIsHoveringOnParticipant] = useState<boolean>(false);

    const participantDisplayName = useMemo(() => {
        return isHoveringOnParticipant ? message.x500name : message.x500name.substring(0, 18) + '...';
    }, [isHoveringOnParticipant]);

    return (
        <div
            onMouseEnter={() => {
                setIsHoveringOnParticipant(true);
            }}
            onMouseLeave={() => {
                setIsHoveringOnParticipant(false);
            }}
            className={`${style.messageWrapper} ${isMyMessage ? style.myMessage : ''}`}
        >
            <div className={`${style.messageContainer} ${isMyMessage ? 'ml-auto mr-4' : 'mr-auto ml-0'}`}>
                <p className={`text-xs ml-2 ${isMyMessage ? 'font-bold' : 'font-bold opacity-75'}`}>
                    {isMyMessage ? 'Me' : participantDisplayName}
                </p>
                <div className="flex">
                    <div
                        className={`${style.messageTextContainer} ${
                            isHoveringOnParticipant && !isMyMessage ? 'shadow-xl' : 'shadow-md'
                        }  ${isMyMessage ? 'bg-blue-100' : ''}`}
                        style={{ maxWidth: isMyMessage ? '100%' : '90%' }}
                    >
                        <p className="leading-5">{message.message}</p>
                    </div>
                    {isHoveringOnParticipant && !isMyMessage && (
                        <IconButton
                            onClick={() => {
                                selectReplyParticipant(message.x500name);
                            }}
                            className={style.replyButton}
                            icon={'Reply'}
                            size={'medium'}
                            variant={'tertiary'}
                        />
                    )}
                </div>
            </div>
        </div>
    );
};

export default Message;
