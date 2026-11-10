import type { ImgHTMLAttributes } from "react";
import logoLight from "../assets/logo-light.png";
import logoDark from "../assets/logo-dark.png";
import { useOptionalTheme, type Theme } from "../theme/ThemeProvider";

type Props = Omit<
  ImgHTMLAttributes<HTMLImageElement>,
  "src" | "width" | "height"
> & { size?: number; theme?: Theme };
export function Logo({
  size = 44,
  className = "",
  alt = "BookRush",
  theme: requestedTheme,
  ...props
}: Props) {
  const context = useOptionalTheme();
  const theme = requestedTheme ?? context?.theme ?? "light";
  return (
    <img
      {...props}
      src={theme === "dark" ? logoDark : logoLight}
      alt={alt}
      width={size}
      height={size}
      className={`br-logo ${className}`}
    />
  );
}
