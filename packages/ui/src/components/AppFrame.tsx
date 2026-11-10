import type { ReactNode } from "react";
export function AppFrame({
  sidebar,
  header,
  children,
  className = "",
  mainId = "bookrush-main",
}: {
  sidebar: ReactNode;
  header: ReactNode;
  children: ReactNode;
  className?: string;
  mainId?: string;
}) {
  return (
    <div className={`br-shell ${className}`}>
      <a className="br-skip-link" href={`#${mainId}`}>
        Pular para conteúdo
      </a>
      {sidebar}
      <div className="br-shell__body">
        {header}
        <main id={mainId} tabIndex={-1} className="br-reader-main">
          {children}
        </main>
      </div>
    </div>
  );
}
