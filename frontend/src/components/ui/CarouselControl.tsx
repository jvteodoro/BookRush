import { forwardRef, type ButtonHTMLAttributes } from 'react';
import { Icon } from './Icon';

type Props = Omit<ButtonHTMLAttributes<HTMLButtonElement>, 'children' | 'aria-label'> & { direction: 'previous' | 'next'; label: string };
export const CarouselControl = forwardRef<HTMLButtonElement, Props>(function CarouselControl({ direction, label, className = '', type = 'button', ...props }, ref) {
  return <button {...props} type={type} ref={ref} aria-label={label} title={label} className={`br-carousel-control br-carousel-control--${direction} ${className}`}><Icon name="arrow" /></button>;
});
