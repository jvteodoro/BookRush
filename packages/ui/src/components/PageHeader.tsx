import type { HTMLAttributes } from "react";
export function PageHeader({
  className = "",
  ...props
}: HTMLAttributes<HTMLElement>) {
  return <header {...props} className={`br-page-header ${className}`} />;
}
