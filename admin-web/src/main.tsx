import React from 'react';
import ReactDOM from 'react-dom/client';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { AuthProvider } from './auth/AuthProvider';
import { App } from './app/App';
import { ErrorBoundary } from './app/ErrorBoundary';
import './styles/app.css';
const qc=new QueryClient({defaultOptions:{queries:{staleTime:15000,retry:1}}});
ReactDOM.createRoot(document.getElementById('root')!).render(<React.StrictMode><ErrorBoundary><QueryClientProvider client={qc}><AuthProvider><App/></AuthProvider></QueryClientProvider></ErrorBoundary></React.StrictMode>);
