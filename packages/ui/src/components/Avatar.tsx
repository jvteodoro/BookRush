import type { ButtonHTMLAttributes } from "react";
export function Avatar({
  name,
  ...props
}: ButtonHTMLAttributes<HTMLButtonElement> & { name: string }) {
  return (
    <button
      {...props}
      type="button"
      className="br-avatar"
      aria-label={props["aria-label"] ?? `Abrir perfil de ${name}`}
    >
      {name.trim().slice(0, 1).toUpperCase() || "L"}
    </button>
  );
}
