import Chat from '@/components/VNodeHome/Chat';
import ChatParticipants from '@/components/VNodeHome/ChatParticipants';
import NodeDetails from '@/components/VNodeHome/NodeDetails';
import PageContentWrapper from '@/components/PageContentWrapper/PageContentWrapper';
import PageHeader from '@/components/PageHeader/PageHeader';
import Section from '@/components/VNodeHome/Section';
import VNodeHomeViz from '@/components/Visualizations/VNodeHomeViz';
import VisualizationWrapper from '@/components/Visualizations/VisualizationWrapper';
import { useState } from 'react';

const VNodeHome = () => {
    const [selectedParticipants, setSelectedParticipants] = useState<string[]>([]);
    return (
        <PageContentWrapper>
            <div className="mt-24 sm:mt-0 md:mt-0 lg:mt-0" />
            <PageHeader withBackButton>V-Node Home</PageHeader>
            <div className="flex flew-col gap-4 mt-8 ml-4 flex-wrap">
                <Section title={'Chat'}>
                    <Chat selectedParticipants={selectedParticipants} />
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
