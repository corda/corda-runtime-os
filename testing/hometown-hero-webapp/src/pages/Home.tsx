import { LOGIN, REGISTER } from '../constants/routes';

import { Button } from '@r3/r3-tooling-design-system/exports';
import HomeViz from '../components/Visualizations/HomeViz';
import PageContentWrapper from '../components/PageContentWrapper/PageContentWrapper';
import PageHeader from '../components/PageHeader/PageHeader';
import VisualizationWrapper from '../components/Visualizations/VisualizationWrapper';

const Home = () => {
    return (
        <PageContentWrapper>
            <div className="mt-24 sm:mt-0 md:mt-0 lg:mt-0" />
            <PageHeader>Hometown Hero</PageHeader>
            <div className="flex gap-4 mt-8 ml-4 flex-wrap">
                <Button isLink to={LOGIN} iconLeft={'LoginVariant'} size={'large'} variant={'primary'}>
                    Sign in
                </Button>
                <Button isLink to={REGISTER} iconLeft={'AccountPlus'} size={'large'} variant={'primary'}>
                    Register
                </Button>
            </div>
            <VisualizationWrapper width={1200}>
                <HomeViz qrContent={document.location.href} />
            </VisualizationWrapper>
        </PageContentWrapper>
    );
};

export default Home;
