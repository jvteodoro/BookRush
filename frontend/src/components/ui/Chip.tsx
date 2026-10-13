import { forwardRef, type ButtonHTMLAttributes } from 'react';

type Props = Omit<ButtonHTMLAttributes<HTMLButtonElement>, 'aria-pressed'> & { active?: boolean };
export const Chip = forwardRef<HTMLButtonElement, Props>(function Chip({ active = false, className = '', type = 'button', ...props }, ref) {
  return <button ref={ref} type={type} {...props} aria-pressed={active} className={`br-chip${active ? ' br-chip--active' : ''} ${className}`} />;
});
