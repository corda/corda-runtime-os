import Chat from '@/components/VNodeHome/Chat/Chat';
import ChatParticipants from '@/components/VNodeHome/Chat/ChatParticipants';
import NodeDetails from '@/components/VNodeHome/NodeDetails/NodeDetails';
import PageContentWrapper from '@/components/PageContentWrapper/PageContentWrapper';
import PageHeader from '@/components/PageHeader/PageHeader';
import Section from '@/components/VNodeHome/Section/Section';
import VNodeHomeViz from '@/components/Visualizations/VNodeHomeViz';
import VisualizationWrapper from '@/components/Visualizations/VisualizationWrapper';
import { useState } from 'react';

const VNodeHome = () => {
    const [selectedParticipants, setSelectedParticipants] = useState<string[]>([]);

    const handleSelectReplyParticipant = (participant: string) => {
        setSelectedParticipants([participant]);
    };

    console.log('VNODE HOME', selectedParticipants);

    return (
        <PageContentWrapper>
            <div className="mt-24 sm:mt-0 md:mt-0 lg:mt-0" />
            <PageHeader withBackButton>V-Node Home</PageHeader>
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
            <VisualizationWrapper width={520}>
                <VNodeHomeViz />
            </VisualizationWrapper>
        </PageContentWrapper>
    );
};

export default VNodeHome;
