import NetworkVisualizer from '@/components/NetworkVisualizer/NetworkVisualizer';
import PageContentWrapper from '@/components/PageContentWrapper/PageContentWrapper';
import PageHeader from '@/components/PageHeader/PageHeader';
import VNodeNetworkViz from '@/components/Visualizations/VNodeNetworkViz';
import VisualizationWrapper from '@/components/Visualizations/VisualizationWrapper';

const VNodeNetwork = () => {
    return (
        <PageContentWrapper>
            <PageHeader withBackButton>V-Node Network</PageHeader>
            <NetworkVisualizer />
            <VisualizationWrapper width={600}>
                <VNodeNetworkViz />
            </VisualizationWrapper>
        </PageContentWrapper>
    );
};

export default VNodeNetwork;
