import type { HTMLAttributes } from "react";
export function Surface({
  as: Element = "div",
  className = "",
  ...props
}: HTMLAttributes<HTMLElement> & { as?: "div" | "section" | "article" }) {
  return <Element {...props} className={`br-card br-surface ${className}`} />;
}
