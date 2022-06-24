import './App.scss';

import { HOME, LOGIN, REGISTER, VNODE_HOME } from './constants/routes';
import { Route, Routes } from 'react-router';

import { BrowserRouter } from 'react-router-dom';
import Footer from './components/Footer/Footer';
import Home from './pages/Home';
import LoadingModal from './components/LoadingModal/LoadingModal';
import Login from './pages/Login';
import NavBar from './components/NavBar/NavBar';
import Register from './pages/Register';
import { UserContextProvider } from './contexts/userContext';
import VNodeHome from './pages/VNodeHome';
import { useMobileMediaQuery } from './hooks/useMediaQueries';
import { usePromiseTracker } from 'react-promise-tracker';

function App() {
    const isMobile = useMobileMediaQuery();
    const { promiseInProgress } = usePromiseTracker();
    return (
        <div className="App">
            {promiseInProgress && <LoadingModal />}
            <BrowserRouter>
                <UserContextProvider>
                    <NavBar />

                    <Routes>
                        <Route path={HOME} element={<Home />} />
                        <Route path={LOGIN} element={<Login />} />
                        <Route path={REGISTER} element={<Register />} />
                        <Route path={VNODE_HOME} element={<VNodeHome />} />
                    </Routes>
                    {!isMobile && <Footer />}
                </UserContextProvider>
            </BrowserRouter>
        </div>
    );
}

export default App;
