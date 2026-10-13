import { forwardRef, type ButtonHTMLAttributes } from 'react';

type Props = ButtonHTMLAttributes<HTMLButtonElement> & {
  variant?: 'primary' | 'secondary' | 'ghost';
  size?: 'sm' | 'md' | 'lg';
  fullWidth?: boolean;
};

export const Button = forwardRef<HTMLButtonElement, Props>(function Button(
  { variant = 'primary', size = 'md', fullWidth = false, className = '', type = 'button', ...props }, ref,
) {
  return <button ref={ref} type={type} className={`br-button br-button--${variant} br-button--${size}${fullWidth ? ' br-button--full' : ''} ${className}`} {...props} />;
});
