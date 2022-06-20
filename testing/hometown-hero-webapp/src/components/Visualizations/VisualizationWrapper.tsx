type Props = {
    //An optional prop for width, height is calculated automatically
    width?: number;
    isBlurred?: boolean;
    children?: React.ReactNode;
};

const VisualizationWrapper: React.FC<Props> = ({ children, isBlurred, width = 500 }) => {
    return (
        <div
            style={{
                position: 'absolute',
                height: 'auto',
                width: width,
                bottom: '10%',
                right: '2%',
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
