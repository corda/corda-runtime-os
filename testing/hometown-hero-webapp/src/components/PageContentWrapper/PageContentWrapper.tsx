import { useMobileMediaQuery } from '@/hooks/useMediaQueries';

type Props = {
    children?: React.ReactNode;
    footerEnabled?: boolean;
};

const PageContentWrapper: React.FC<Props> = ({ children, footerEnabled = true }) => {
    const isMobile = useMobileMediaQuery();
    return (
        <>
            <div
                className={`${isMobile ? 'pl-2' : 'pl-8'} pt-2`}
                style={{
                    minHeight: !isMobile ? '95vh' : undefined,
                    display: 'flex',
                    flexDirection: 'column',
                    height: 'calc(100vh - 64px)',
                }}
            >
                {children}
            </div>
        </>
    );
};

export default PageContentWrapper;
