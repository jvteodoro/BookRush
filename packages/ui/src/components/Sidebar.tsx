import { useLayoutEffect, useRef, useState, type ReactNode } from "react";
import { Icon } from "./Icon";

export type SidebarItem = {
  id: string;
  label: string;
  icon: ReactNode;
  separator?: boolean;
  desktopLabel?: ReactNode;
  href?: string;
};
export type SidebarProps = {
  id: string;
  items: readonly SidebarItem[];
  active: string;
  onNavigate: (id: string) => void;
  logo: ReactNode;
  subtitle?: string;
  brandLabel?: string;
  onBrandClick: () => void;
  collapsed: boolean;
  onCollapsedChange: (collapsed: boolean) => void;
  footer?: ReactNode;
  mobileNavigation?: boolean;
  navigationLabel?: string;
};

export function Sidebar({
  id,
  items,
  active,
  onNavigate,
  logo,
  subtitle,
  brandLabel = "BookRush",
  onBrandClick,
  collapsed,
  onCollapsedChange,
  footer,
  mobileNavigation = true,
  navigationLabel = "Navegação principal",
}: SidebarProps) {
  const navRef = useRef<HTMLElement>(null);
  const [indicator, setIndicator] = useState({ top: 0, height: 48 });
  useLayoutEffect(() => {
    const nav = navRef.current;
    if (!nav) return;
    let disposed = false;
    const update = () => {
      if (disposed) return;
      const selected = nav.querySelector<HTMLElement>('[aria-current="page"]');
      if (selected)
        setIndicator({
          top: selected.offsetTop,
          height: selected.offsetHeight,
        });
    };
    update();
    const observer = new ResizeObserver(update);
    observer.observe(nav);
    document.fonts?.ready.then(update);
    return () => {
      disposed = true;
      observer.disconnect();
    };
  }, [active, collapsed, items]);
  const navigation = (mobile: boolean) => (
    <nav
      ref={mobile ? undefined : navRef}
      aria-label={mobile ? `${navigationLabel} mobile` : navigationLabel}
      className={mobile ? "br-tabbar" : "br-sidebar__nav"}
    >
      {!mobile && (
        <span
          aria-hidden="true"
          className="br-nav-indicator"
          style={{
            height: indicator.height,
            transform: `translateY(${indicator.top}px)`,
          }}
        />
      )}
      {items.map((item) => {
        const content = (
          <>
            {item.icon}
            <span className="br-nav-label">
              {!mobile && item.desktopLabel ? item.desktopLabel : item.label}
            </span>
          </>
        );
        const props = {
          className: `br-sidebar__item${!mobile && item.separator ? " br-nav-separator" : ""}`,
          "aria-current": active === item.id ? ("page" as const) : undefined,
          "aria-label": item.label,
          title: item.label,
        };
        return item.href ? (
          <a
            key={item.id}
            {...props}
            href={item.href}
            onClick={(event) => {
              if (
                event.button !== 0 ||
                event.metaKey ||
                event.ctrlKey ||
                event.shiftKey ||
                event.altKey
              )
                return;
              event.preventDefault();
              onNavigate(item.id);
            }}
          >
            {content}
          </a>
        ) : (
          <button
            key={item.id}
            {...props}
            type="button"
            onClick={() => onNavigate(item.id)}
          >
            {content}
          </button>
        );
      })}
    </nav>
  );
  return (
    <>
      <aside className="br-sidebar" id={id} data-collapsed={collapsed}>
        <div className="br-sidebar__top">
          <button
            type="button"
            className="br-sidebar__brand"
            onClick={onBrandClick}
            aria-label={brandLabel}
          >
            {logo}
            <span className="br-nav-label br-sidebar__brand-copy">
              <span>BookRush</span>
              {subtitle && <small>{subtitle}</small>}
            </span>
          </button>
          <button
            className="br-sidebar-toggle"
            type="button"
            aria-controls={id}
            aria-expanded={!collapsed}
            aria-label={collapsed ? "Expandir menu" : "Recolher menu"}
            title={collapsed ? "Expandir menu" : "Recolher menu"}
            onClick={() => onCollapsedChange(!collapsed)}
          >
            <Icon name="panelClose" />
          </button>
        </div>
        {navigation(false)}
        {footer && <div className="br-sidebar__foot">{footer}</div>}
      </aside>
      {mobileNavigation && navigation(true)}
    </>
  );
}
