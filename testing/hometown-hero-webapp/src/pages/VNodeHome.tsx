import ChatWrapper from '@/components/VNodeHome/Chat/ChatWrapper';
import PageContentWrapper from '@/components/PageContentWrapper/PageContentWrapper';
import PageHeader from '@/components/PageHeader/PageHeader';
import VNodeHomeViz from '@/components/Visualizations/VNodeHomeViz';
import VisualizationWrapper from '@/components/Visualizations/VisualizationWrapper';
import { useMobileMediaQuery } from '@/hooks/useMediaQueries';

const VNodeHome = () => {
    const isMobile = useMobileMediaQuery();
    return (
        <PageContentWrapper footerEnabled={isMobile ? false : true}>
            <PageHeader>V-Node Home</PageHeader>
            <ChatWrapper />
            {!isMobile && (
                <VisualizationWrapper width={520}>
                    <VNodeHomeViz />
                </VisualizationWrapper>
            )}
        </PageContentWrapper>
    );
};

export default VNodeHome;
