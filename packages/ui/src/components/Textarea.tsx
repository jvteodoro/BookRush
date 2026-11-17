import { forwardRef, type TextareaHTMLAttributes } from "react";
export const Textarea = forwardRef<
  HTMLTextAreaElement,
  TextareaHTMLAttributes<HTMLTextAreaElement>
>(function Textarea({ className = "", ...props }, ref) {
  return (
    <textarea
      {...props}
      ref={ref}
      className={`br-input br-textarea ${className}`}
    />
  );
});
