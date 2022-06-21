import { useDesktopMediaQuery, useMobileMediaQuery, useTabletMediaQuery } from '../../hooks/useMediaQueries';

type Props = {
    //An optional prop for width, height is calculated automatically
    width?: number;
    isBlurred?: boolean;
    children?: React.ReactNode;
};

const VisualizationWrapper: React.FC<Props> = ({ children, isBlurred, width = 500 }) => {
    const isMobile = useMobileMediaQuery();
    const isTablet = useTabletMediaQuery();
    const vizWidth = isMobile ? '90%' : isTablet ? width * 0.8 : width;
    return (
        <div
            style={{
                position: 'absolute',
                height: 'auto',
                width: vizWidth,
                bottom: isMobile ? 0 : '10%',
                right: isMobile ? 0 : '2%',
                zIndex: -2,
                opacity: 0.6,
                transition: 'ease-in-out 0.5s',
                filter: `${isBlurred ? 'blur(8px)' : ''}`,
            }}
        >
            {children}
        </div>
    );
};

export default VisualizationWrapper;
