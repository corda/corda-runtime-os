import { Container } from '@r3/r3-tooling-design-system/exports';
import { useMobileMediaQuery } from '@/hooks/useMediaQueries';

type Props = {
    children?: React.ReactNode;
};

const PageContentWrapper: React.FC<Props> = ({ children }) => {
    const isMobile = useMobileMediaQuery();
    return (
        <Container className={`${isMobile ? 'pl-4' : 'pl-8'} pt-2`} fluid style={{ minHeight: '95vh' }}>
            {children}
        </Container>
    );
};

export default PageContentWrapper;
