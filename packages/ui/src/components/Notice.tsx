import type { HTMLAttributes } from "react";
export function Notice({
  as: Element = "div",
  className = "",
  ...props
}: HTMLAttributes<HTMLElement> & { as?: "div" | "p" | "section" }) {
  return <Element {...props} className={`br-notice ${className}`} />;
}
