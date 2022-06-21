import { Container } from '@r3/r3-tooling-design-system/exports';

type Props = {
    children?: React.ReactNode;
};

const PageContentWrapper: React.FC<Props> = ({ children }) => {
    return (
        <Container className="pl-8 pt-2" fluid style={{ minHeight: '95vh' }}>
            {children}
        </Container>
    );
};

export default PageContentWrapper;
