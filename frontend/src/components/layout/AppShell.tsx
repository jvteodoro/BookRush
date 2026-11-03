import { useLayoutEffect, useRef, useState, type ReactNode } from 'react';
import { Logo } from '../ui/Logo';
import { Button } from '../ui/Button';
import { useTheme } from '../../theme/ThemeProvider';
import { Icon, type IconName } from '../ui/Icon';

export type ReaderTab = 'feed' | 'recommendations' | 'search' | 'library' | 'plant' | 'profile' | 'privacy' | 'admin' | 'publisher' | 'prototype';
const tabs: { id: ReaderTab; label: string; icon: IconName; separator?: boolean }[] = [
  { id: 'feed', label: 'Para você', icon: 'home' },
  { id: 'recommendations', label: 'Recomendações', icon: 'sparkle' },
  { id: 'search', label: 'Buscar', icon: 'search' },
  { id: 'library', label: 'Biblioteca', icon: 'library' },
  { id: 'plant', label: 'Minha planta', icon: 'leaf' },
  { id: 'profile', label: 'Perfil', icon: 'user' },
  { id: 'privacy', label: 'Privacidade', icon: 'shield' },
  { id: 'admin', label: 'Administração', icon: 'admin', separator: true },
  { id: 'publisher', label: 'Publicador', icon: 'upload', separator: true },
  { id: 'prototype', label: 'Menu do protótipo', icon: 'grid', separator: true },
];

type Props = { children: ReactNode; active: ReaderTab; onNavigate: (tab: ReaderTab) => void; query: string; onSearch: (query: string) => void; userName?: string };
export function AppShell({ children, active, onNavigate, query, onSearch, userName = 'Ana' }: Props) {
  const [collapsed, setCollapsed] = useState(false);
  const sidebarNav = useRef<HTMLElement>(null);
  const [indicator, setIndicator] = useState({ top: 0, height: 48 });
  useLayoutEffect(() => {
    const nav = sidebarNav.current;
    if (!nav) return;
    const update = () => {
      const selected = nav.querySelector<HTMLElement>('[aria-current="page"]');
      if (!selected) return;
      setIndicator({ top: selected.offsetTop, height: selected.offsetHeight });
    };
    update();
    const observer = new window.ResizeObserver(update);
    observer.observe(nav);
    return () => observer.disconnect();
  }, [active, collapsed]);
  const { theme, toggle } = useTheme();
  const navigation = (mobile: boolean) => <nav ref={mobile ? undefined : sidebarNav} aria-label={mobile ? 'Navegação mobile' : 'Navegação principal'} className={mobile ? 'br-tabbar' : 'br-sidebar__nav'}>
    {!mobile && <span aria-hidden="true" className="br-nav-indicator" style={{ height: indicator.height, transform: `translateY(${indicator.top}px)` }} />}
    {tabs.map(tab => <button key={tab.id} type="button" className={!mobile && tab.separator ? 'br-nav-separator' : undefined} aria-current={active === tab.id ? 'page' : undefined} aria-label={tab.label} title={tab.label} onClick={() => onNavigate(tab.id)}>
      <Icon name={tab.icon} /><span className="br-nav-label">{!mobile && tab.id === 'prototype' ? <>Menu do<br />protótipo</> : tab.label}</span>
    </button>)}
  </nav>;
  return <div className={`br-shell${collapsed ? ' br-shell--collapsed' : ''}`}>
    <a className="br-skip-link" href="#reader-main">Pular para conteúdo</a>
    <aside className="br-sidebar" id="reader-sidebar">
      <div className="br-sidebar__top"><button type="button" className="br-sidebar__brand" onClick={() => onNavigate('feed')} aria-label="BookRush — Para você"><Logo /><span className="br-nav-label">BookRush</span></button>
        <button className="br-sidebar-toggle" type="button" aria-controls="reader-sidebar" aria-expanded={!collapsed} aria-label={collapsed ? 'Expandir menu' : 'Recolher menu'} title={collapsed ? 'Expandir menu' : 'Recolher menu'} onClick={() => setCollapsed(value => !value)}><Icon name="panelClose" /></button>
      </div>
      {navigation(false)}
    </aside>
    <div className="br-shell__body">
      <header className="br-header">
        <div className="br-mobile-logo"><Logo size={36} /></div>
        <span className="br-header__title">{tabs.find(tab => tab.id === active)?.label}</span>
        <div className="br-header__center"><button type="button" className="br-home-shortcut" aria-current={active === 'feed' ? 'page' : undefined} aria-label="Ir para Para você" title="Para você" onClick={() => onNavigate('feed')}><Icon name="home" /></button>
        <form role="search" className="br-header__search" onSubmit={event => { event.preventDefault(); onNavigate('search'); }}>
          <label className="br-sr-only" htmlFor="header-search">Buscar livros ou pessoas</label>
          <Icon name="search" /><input id="header-search" value={query} onChange={event => onSearch(event.target.value)} placeholder="Buscar livros, pessoas…" /><button type="submit" aria-label="Buscar"><Icon name="arrow" /></button>
        </form></div>
        <div className="br-header__actions"><Button className="br-theme-toggle" variant="ghost" size="sm" onClick={toggle} aria-label={`Ativar tema ${theme === 'dark' ? 'claro' : 'escuro'}`}><Icon name={theme === 'dark' ? 'sun' : 'moon'} /></Button>
        <button className="br-avatar" type="button" aria-label={`Abrir perfil de ${userName}`} onClick={() => onNavigate('profile')}>{userName.trim().slice(0, 1).toUpperCase() || 'L'}</button></div>
      </header>
      <main id="reader-main" tabIndex={-1} className="br-reader-main">{children}</main>
    </div>
    {navigation(true)}
  </div>;
}
