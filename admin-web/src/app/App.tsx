import { createBrowserRouter, RouterProvider } from 'react-router-dom';
import { Layout } from '../components/Layout';
import { Dashboard } from './Dashboard';
import { CatalogPage } from '../features/catalog/CatalogPage';
import { AnalyticsPage } from '../features/analytics/AnalyticsPage';
import { FeedLabPage } from '../features/feedlab/FeedLabPage';
import { useAuth } from '../auth/useAuth';
import { config } from '../lib/config';
import { TrainingPage } from '../features/training/TrainingPage';

const router=createBrowserRouter([{path:'/',element:<Layout/>,children:[{index:true,element:<Dashboard/>},{path:'catalog',element:<CatalogPage/>},{path:'analytics',element:<AnalyticsPage/>},{path:'feed-lab',element:<FeedLabPage/>},{path:'training',element:<TrainingPage/>}]}], { basename: config.basePath.replace(/\/$/, '') || undefined });
export function App(){const a=useAuth();if(!a.ready)return <div className="center">Connecting to Keycloak…</div>;if(!a.authenticated)return <div className="center"><button onClick={a.login}>Sign in</button></div>;return <RouterProvider router={router}/>}
