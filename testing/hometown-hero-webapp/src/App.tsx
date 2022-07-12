import './App.scss';

import { CPI_UPLOAD, HOME, LOGIN, REGISTER, VNODE_HOME, VNODE_NETWORK } from './constants/routes';
import { Route, Routes } from 'react-router';

import Admin from './pages/Admin';
import { AppDataContextProvider } from './contexts/appDataContext';
import { BrowserRouter } from 'react-router-dom';
import Footer from './components/Footer/Footer';
import Home from './pages/Home';
import LoadingModal from './components/LoadingModal/LoadingModal';
import Login from './pages/Login';
import NavBar from './components/NavBar/NavBar';
import Register from './pages/Register/Register';
import { UserContextProvider } from './contexts/userContext';
import VNodeHome from './pages/VNodeHome';
import VNodeNetwork from './pages/VNodeNetwork';
import { useMobileMediaQuery } from './hooks/useMediaQueries';
import { usePromiseTracker } from 'react-promise-tracker';

function App() {
    const isMobile = useMobileMediaQuery();
    const { promiseInProgress } = usePromiseTracker();
    return (
        <div className="App">
            {promiseInProgress && <LoadingModal />}
            <AppDataContextProvider>
                <BrowserRouter>
                    <UserContextProvider>
                        <NavBar />
                        <Routes>
                            <Route path={HOME} element={<Home />} />
                            <Route path={LOGIN} element={<Login />} />
                            <Route path={REGISTER} element={<Register />} />
                            <Route path={VNODE_HOME} element={<VNodeHome />} />
                            <Route path={VNODE_NETWORK} element={<VNodeNetwork />} />
                            <Route path={CPI_UPLOAD} element={<Admin />} />
                        </Routes>
                        {!isMobile && <Footer />}
                    </UserContextProvider>
                </BrowserRouter>
            </AppDataContextProvider>
        </div>
    );
}

export default App;
