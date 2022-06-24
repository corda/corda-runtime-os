import Chat from './Chat';
import ChatParticipants from './ChatParticipants';
import NodeDetails from '../NodeDetails/NodeDetails';
import PageHeader from '@/components/PageHeader/PageHeader';
import Section from '../Section/Section';
import { useState } from 'react';

const ChatWrapper = () => {
    const [selectedParticipants, setSelectedParticipants] = useState<string[]>([]);

    const handleSelectReplyParticipant = (participant: string) => {
        setSelectedParticipants([participant]);
    };

    return (
        <>
            <PageHeader>V-Node Home</PageHeader>
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
        </>
    );
};

export default ChatWrapper;
