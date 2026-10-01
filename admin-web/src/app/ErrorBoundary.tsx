import { Component, ErrorInfo, PropsWithChildren, ReactNode } from 'react';

type State = { error?: Error };
export class ErrorBoundary extends Component<PropsWithChildren, State> {
  state: State = {};
  static getDerivedStateFromError(error: Error): State { return { error }; }
  componentDidCatch(error: Error, info: ErrorInfo) { console.error('admin-web render failure', { name: error.name, message: error.message, componentStack: info.componentStack }); }
  render(): ReactNode { return this.state.error ? <main className="center"><section className="panel"><h1>Admin console unavailable</h1><p>Ocorreu um erro recuperável na interface.</p><button onClick={() => location.reload()}>Reload</button></section></main> : this.props.children; }
}
