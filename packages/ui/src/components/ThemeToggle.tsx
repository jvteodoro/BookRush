import { Button } from "./Button";
import { Icon } from "./Icon";
import { useTheme } from "../theme/ThemeProvider";
export function ThemeToggle() {
  const { theme, toggle } = useTheme();
  return (
    <Button
      variant="ghost"
      size="sm"
      onClick={toggle}
      aria-label={`Ativar tema ${theme === "dark" ? "claro" : "escuro"}`}
    >
      <Icon name={theme === "dark" ? "sun" : "moon"} />
    </Button>
  );
}
