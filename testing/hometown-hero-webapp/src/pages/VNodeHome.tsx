import ChatWrapper from '@/components/VNodeHome/ChatWrapper/ChatWrapper';
import { MessagesContextProvider } from '@/contexts/messagesContext';
import MobileChatWrapper from '@/components/VNodeHome/MobileChatWrapper/MobileChatWrapper';
import PageContentWrapper from '@/components/PageContentWrapper/PageContentWrapper';
import VNodeHomeViz from '@/components/Visualizations/VNodeHomeViz';
import VisualizationWrapper from '@/components/Visualizations/VisualizationWrapper';
import { useMobileMediaQuery } from '@/hooks/useMediaQueries';

const VNodeHome = () => {
    const isMobile = useMobileMediaQuery();
    return (
        <MessagesContextProvider>
            <PageContentWrapper footerEnabled={isMobile ? false : true}>
                {isMobile ? <MobileChatWrapper /> : <ChatWrapper />}
                {!isMobile && (
                    <VisualizationWrapper width={520}>
                        <VNodeHomeViz />
                    </VisualizationWrapper>
                )}
            </PageContentWrapper>
        </MessagesContextProvider>
    );
};

export default VNodeHome;
