import { useMemo, useState } from 'react';

import Chat from '../ChatComponents/Chat/Chat';
import ChatParticipants from '../ChatComponents/ChatParticipants/ChatParticipants';
import PageHeader from '@/components/PageHeader/PageHeader';
import SelectedParticipants from '../ChatComponents/SelectedParticipants/SelectedParticipants';

const MobileChatWrapper = () => {
    const [isParticipantsOpen, setIsParticipantsOpen] = useState<boolean>(false);
    const [selectedParticipants, setSelectedParticipants] = useState<string[]>([]);

    const handleSelectReplyParticipant = (participant: string) => {
        setSelectedParticipants([participant]);
    };

    const pageHeaderText = useMemo(() => {
        return isParticipantsOpen ? 'Chat to' : 'Chat Home';
    }, [isParticipantsOpen]);

    return (
        <>
            <PageHeader>{pageHeaderText}</PageHeader>
            {isParticipantsOpen ? (
                <ChatParticipants
                    selectedParticipants={selectedParticipants}
                    setSelectedParticipants={setSelectedParticipants}
                    handleCloseParticipants={() => {
                        setIsParticipantsOpen(false);
                    }}
                />
            ) : (
                <>
                    <div className="ml-4 mb-4">
                        <SelectedParticipants
                            selectedParticipants={selectedParticipants}
                            handleClearParticipants={() => {
                                setSelectedParticipants([]);
                            }}
                            clearButtonEnabled={false}
                        />
                    </div>
                    <Chat
                        selectedParticipants={selectedParticipants}
                        handleSelectReplyParticipant={handleSelectReplyParticipant}
                        handleOpenParticipantsModal={() => {
                            setIsParticipantsOpen(true);
                        }}
                    />
                </>
            )}
        </>
    );
};

export default MobileChatWrapper;
