import Chat from '@/components/VNodeHome/Chat/Chat';
import ChatParticipants from '@/components/VNodeHome/Chat/ChatParticipants';
import ChatWrapper from '@/components/VNodeHome/Chat/ChatWrapper';
import NodeDetails from '@/components/VNodeHome/NodeDetails/NodeDetails';
import PageContentWrapper from '@/components/PageContentWrapper/PageContentWrapper';
import PageHeader from '@/components/PageHeader/PageHeader';
import Section from '@/components/VNodeHome/Section/Section';
import VNodeHomeViz from '@/components/Visualizations/VNodeHomeViz';
import VisualizationWrapper from '@/components/Visualizations/VisualizationWrapper';
import { useState } from 'react';

const VNodeHome = () => {
    return (
        <PageContentWrapper>
            <div className="mt-24 sm:mt-0 md:mt-0 lg:mt-0" />
            <PageHeader withBackButton>V-Node Home</PageHeader>
            <ChatWrapper />
            <VisualizationWrapper width={520}>
                <VNodeHomeViz />
            </VisualizationWrapper>
        </PageContentWrapper>
    );
};

export default VNodeHome;
