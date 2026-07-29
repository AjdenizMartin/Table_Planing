import type {
  InputHTMLAttributes,
  ReactNode,
  SelectHTMLAttributes,
  TextareaHTMLAttributes,
} from "react";

export function TextField({
  label,
  hint,
  ...props
}: InputHTMLAttributes<HTMLInputElement> & {
  label: string;
  hint?: string;
}) {
  return (
    <label className="grid min-w-0 gap-2">
      <span className="text-sm font-medium text-slate-200">{label}</span>
      <input
        {...props}
        className={[
          "h-11 w-full min-w-0 rounded-lg border border-white/10 bg-[#0c100e] px-3 text-sm text-white outline-none transition focus:border-emerald-400/70 focus:ring-2 focus:ring-emerald-400/20",
          props.className ?? "",
        ].join(" ")}
      />
      {hint ? <span className="text-xs text-slate-500">{hint}</span> : null}
    </label>
  );
}

export function SelectField({
  label,
  children,
  ...props
}: SelectHTMLAttributes<HTMLSelectElement> & {
  label: string;
  children: ReactNode;
}) {
  return (
    <label className="grid min-w-0 gap-2">
      <span className="text-sm font-medium text-slate-200">{label}</span>
      <select
        {...props}
        className={[
          "h-11 w-full min-w-0 rounded-lg border border-white/10 bg-[#0c100e] px-3 text-sm text-white outline-none transition focus:border-emerald-400/70 focus:ring-2 focus:ring-emerald-400/20",
          props.className ?? "",
        ].join(" ")}
      >
        {children}
      </select>
    </label>
  );
}

export function CheckboxField({
  label,
  checked,
  onChange,
}: {
  label: string;
  checked: boolean;
  onChange: (checked: boolean) => void;
}) {
  return (
    <label className="flex min-h-11 min-w-0 items-center gap-3 rounded-lg border border-white/10 bg-[#0c100e] px-3 py-2.5">
      <input
        className="h-5 w-5 accent-brand-400"
        type="checkbox"
        checked={checked}
        onChange={(event) => onChange(event.target.checked)}
      />
      <span className="min-w-0 text-sm font-medium text-slate-200">{label}</span>
    </label>
  );
}

export function TextAreaField({
  label,
  hint,
  ...props
}: TextareaHTMLAttributes<HTMLTextAreaElement> & {
  label: string;
  hint?: string;
}) {
  return (
    <label className="grid min-w-0 gap-2">
      <span className="text-sm font-medium text-slate-200">{label}</span>
      <textarea
        {...props}
        className={[
          "min-h-24 w-full min-w-0 rounded-lg border border-white/10 bg-[#0c100e] px-3 py-3 text-sm text-white outline-none transition focus:border-emerald-400/70 focus:ring-2 focus:ring-emerald-400/20",
          props.className ?? "",
        ].join(" ")}
      />
      {hint ? <span className="text-xs text-slate-500">{hint}</span> : null}
    </label>
  );
}
