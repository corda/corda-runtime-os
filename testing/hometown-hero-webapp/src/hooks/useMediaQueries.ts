import { useMediaQuery } from 'react-responsive';

export const useDesktopMediaQuery = () => useMediaQuery({ minWidth: 1366 });
export const useTabletMediaQuery = () => useMediaQuery({ minWidth: 768, maxWidth: 1366 });
export const useMobileMediaQuery = () => useMediaQuery({ maxWidth: 768 });
