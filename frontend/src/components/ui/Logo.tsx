import type { ImgHTMLAttributes } from 'react';
import logoLight from '../../assets/logo-light.png';
import logoDark from '../../assets/logo-dark.png';
import { useTheme } from '../../theme/ThemeProvider';

type Props = Omit<ImgHTMLAttributes<HTMLImageElement>, 'src' | 'width' | 'height'> & { size?: number };
export function Logo({ size = 44, className = '', alt = 'BookRush', ...props }: Props) {
  const { theme } = useTheme();
  return <img {...props} src={theme === 'dark' ? logoDark : logoLight} alt={alt} width={size} height={size} className={`br-logo ${className}`} />;
}
