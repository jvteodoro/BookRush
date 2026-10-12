import type { HTMLAttributes } from 'react';

type Props = Omit<HTMLAttributes<HTMLDivElement>, 'role' | 'children' | 'aria-valuenow' | 'aria-valuemin' | 'aria-valuemax'> & { value: number; label: string };
export function ProgressBar({ value, label, className = '', ...props }: Props) {
  const percent = Number.isFinite(value) ? Math.min(100, Math.max(0, value)) : 0;
  return <div {...props} role="progressbar" aria-label={label} aria-valuenow={percent} aria-valuemin={0} aria-valuemax={100} className={`br-progress ${className}`}>
    <div className="br-progress__fill" style={{ width: `${percent}%` }} />
  </div>;
}
