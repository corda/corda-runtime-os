import Chat from './Chat';
import ChatParticipants from './ChatParticipants';
import NodeDetails from '../NodeDetails/NodeDetails';
import Section from '../Section/Section';
import { useMobileMediaQuery } from '@/hooks/useMediaQueries';
import { useState } from 'react';

const ChatWrapper = () => {
    const isMobile = useMobileMediaQuery();
    const [isParticipantsOpen, setIsParticipantsOpen] = useState<boolean>(false);

    const [selectedParticipants, setSelectedParticipants] = useState<string[]>([]);

    const handleSelectReplyParticipant = (participant: string) => {
        setSelectedParticipants([participant]);
    };

    if (isMobile) {
        if (isParticipantsOpen) {
            return (
                <ChatParticipants
                    selectedParticipants={selectedParticipants}
                    setSelectedParticipants={setSelectedParticipants}
                    handleCloseParticipants={() => {
                        setIsParticipantsOpen(false);
                    }}
                />
            );
        }
        return (
            <Chat
                selectedParticipants={selectedParticipants}
                handleSelectReplyParticipant={handleSelectReplyParticipant}
                handleOpenParticipantsModal={() => {
                    setIsParticipantsOpen(true);
                }}
            />
        );
    }

    return (
        <div className="flex flew-col gap-4 mt-8 ml-4 flex-wrap">
            <Section title={'Chat'}>
                <Chat
                    selectedParticipants={selectedParticipants}
                    handleSelectReplyParticipant={handleSelectReplyParticipant}
                />
            </Section>
            <Section title={'Participants'}>
                <ChatParticipants
                    selectedParticipants={selectedParticipants}
                    setSelectedParticipants={setSelectedParticipants}
                />
            </Section>
            <Section title={'VNode Details'}>
                <NodeDetails />
            </Section>
        </div>
    );
};

export default ChatWrapper;
