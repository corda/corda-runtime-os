import ChatWrapper from '@/components/VNodeHome/ChatWrapper/ChatWrapper';
import MobileChatWrapper from '@/components/VNodeHome/MobileChatWrapper/MobileChatWrapper';
import PageContentWrapper from '@/components/PageContentWrapper/PageContentWrapper';
import VNodeHomeViz from '@/components/Visualizations/VNodeHomeViz';
import VisualizationWrapper from '@/components/Visualizations/VisualizationWrapper';
import { useMobileMediaQuery } from '@/hooks/useMediaQueries';

const VNodeHome = () => {
    const isMobile = useMobileMediaQuery();
    return (
        <PageContentWrapper footerEnabled={isMobile ? false : true}>
            {isMobile ? <MobileChatWrapper /> : <ChatWrapper />}
            {!isMobile && (
                <VisualizationWrapper width={520}>
                    <VNodeHomeViz />
                </VisualizationWrapper>
            )}
        </PageContentWrapper>
    );
};

export default VNodeHome;
