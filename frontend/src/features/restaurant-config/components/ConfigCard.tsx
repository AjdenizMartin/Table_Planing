import type { PropsWithChildren } from "react";

export function ConfigCard({
  title,
  subtitle,
  children,
}: PropsWithChildren<{ title: string; subtitle?: string }>) {
  return (
    <section className="min-w-0 rounded-[2rem] border border-white/10 bg-slate-950/65 p-5 shadow-2xl shadow-black/20 sm:p-6">
      <header className="mb-5">
        <h2 className="text-xl font-semibold text-white">{title}</h2>
        {subtitle ? (
          <p className="mt-2 text-sm leading-6 text-slate-400">{subtitle}</p>
        ) : null}
      </header>
      {children}
    </section>
  );
}
