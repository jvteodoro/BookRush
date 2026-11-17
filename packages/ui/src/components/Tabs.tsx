import type { HTMLAttributes } from "react";
export function Tabs({
  className = "",
  ...props
}: HTMLAttributes<HTMLDivElement>) {
  return <div role="group" {...props} className={`br-tabs ${className}`} />;
}
