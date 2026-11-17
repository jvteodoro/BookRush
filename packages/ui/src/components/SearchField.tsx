import { useId, type InputHTMLAttributes, type ReactNode } from "react";
import { Icon } from "./Icon";

export function SearchField({
  label = "Buscar",
  action,
  className = "",
  id,
  ...props
}: InputHTMLAttributes<HTMLInputElement> & {
  label?: string;
  action?: ReactNode;
}) {
  const generated = useId();
  const inputId = id ?? generated;
  return (
    <div className={`br-search-control ${className}`}>
      <Icon name="search" />
      <input aria-label={label} {...props} id={inputId} />
      {action}
    </div>
  );
}
