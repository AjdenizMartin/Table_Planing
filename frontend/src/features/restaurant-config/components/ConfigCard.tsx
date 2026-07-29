import type { PropsWithChildren } from "react";

export function ConfigCard({
  title,
  children,
}: PropsWithChildren<{ title: string }>) {
  return (
    <section className="min-w-0 rounded-lg border border-white/8 bg-[#111614] p-5 shadow-lg shadow-black/10 sm:p-6">
      <header className="mb-5 flex min-h-7 items-center">
        <h2 className="text-base font-semibold text-white">{title}</h2>
      </header>
      {children}
    </section>
  );
}
