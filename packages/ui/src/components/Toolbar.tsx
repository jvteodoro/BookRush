import type { HTMLAttributes } from "react";
export function Toolbar({
  className = "",
  ...props
}: HTMLAttributes<HTMLDivElement>) {
  return <div {...props} className={`br-toolbar ${className}`} />;
}
