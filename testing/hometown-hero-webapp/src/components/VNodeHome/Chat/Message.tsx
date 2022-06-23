import { useMemo, useState } from 'react';

import { IconButton } from '@r3/r3-tooling-design-system/exports';
import { Message as MessageType } from '@/models/Message';

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
            className={`m-2 ${isMyMessage ? 'ml-auto mr-0' : 'mr-auto'}`}
            style={{ maxWidth: '70%' }}
        >
            <div style={{ width: 'fit-content' }} className={` ${isMyMessage ? 'ml-auto mr-4' : 'mr-auto ml-0'}`}>
                <p className={`text-xs ml-2 ${isMyMessage ? 'font-bold' : 'font-semibold opacity-50'}`}>
                    {isMyMessage ? 'Me' : participantDisplayName}
                </p>
                <div className="flex">
                    <div
                        className={`mt-0 rounded-xl border border-blue shadow-md p-4 ${
                            isMyMessage ? 'bg-blue-100' : ''
                        }`}
                        style={{ maxWidth: '90%' }}
                    >
                        <p className="leading-5">{message.message}</p>
                    </div>
                    {isHoveringOnParticipant && !isMyMessage && (
                        <IconButton
                            onClick={() => {
                                selectReplyParticipant(message.x500name);
                            }}
                            className=" mb-auto mt-auto ml-2"
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
