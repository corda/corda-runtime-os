import { useMemo, useState } from 'react';

import { IconButton } from '@r3/r3-tooling-design-system/exports';
import style from './message.module.scss';

type Props = {
    counterParty: string;
    message: string;
    isMyMessage: boolean;
    isPending?: boolean;
    selectReplyParticipant: (participant: string) => void;
};

const Message: React.FC<Props> = ({ message, isMyMessage, selectReplyParticipant, counterParty, isPending }) => {
    const [isHoveringOnParticipant, setIsHoveringOnParticipant] = useState<boolean>(false);

    const participantDisplayName = useMemo(() => {
        return isHoveringOnParticipant ? counterParty : counterParty.substring(0, 18) + '...';
    }, [isHoveringOnParticipant]);

    return (
        <div
            // onMouseEnter={() => {
            //     setIsHoveringOnParticipant(true);
            // }}
            // onMouseLeave={() => {
            //     setIsHoveringOnParticipant(false);
            // }}
            className={`${style.messageWrapper} ${isMyMessage ? style.myMessage : ''}`}
        >
            <div className={`${style.messageContainer} ${isMyMessage ? 'ml-auto' : 'mr-auto -ml-4'}`}>
                <p className={`text-xs ml-2 ${isMyMessage ? 'font-bold' : 'font-bold opacity-75'}`}>
                    {isMyMessage ? 'Me' : participantDisplayName}
                </p>
                <div className="flex">
                    <div
                        className={`${style.messageTextContainer} ${
                            isHoveringOnParticipant && !isMyMessage ? 'shadow-xl' : 'shadow-md'
                        }  ${isMyMessage ? 'bg-blue-100' : ''} flex`}
                        style={{ maxWidth: '100%', opacity: isPending ? 0.3 : 1 }}
                    >
                        <p className="leading-5">{message}</p>
                    </div>
                    {isHoveringOnParticipant && !isMyMessage && (
                        <IconButton
                            onClick={() => {
                                selectReplyParticipant(counterParty);
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
