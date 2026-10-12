import { forwardRef, useId, type InputHTMLAttributes } from 'react';

type Props = Omit<InputHTMLAttributes<HTMLInputElement>, 'type' | 'role' | 'size' | 'children'> & { label: string };

export const Switch = forwardRef<HTMLInputElement, Props>(function Switch({ label, id, className = '', disabled, ...props }, ref) {
  const generatedId = useId();
  const inputId = id ?? generatedId;
  return <label className={`br-switch${disabled ? ' br-switch--disabled' : ''} ${className}`} htmlFor={inputId}>
    <span className="br-switch__label">{label}</span>
    <span className="br-switch__control"><input {...props} id={inputId} ref={ref} disabled={disabled} type="checkbox" role="switch" /><span className="br-switch__track" aria-hidden="true"><span className="br-switch__thumb" /></span></span>
  </label>;
});
