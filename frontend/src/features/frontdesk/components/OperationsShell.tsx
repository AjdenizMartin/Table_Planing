import type { PropsWithChildren } from "react";

export function OperationsShell({
  title,
  children,
}: PropsWithChildren<{ title: string }>) {
  return (
    <section className="grid min-w-0 gap-5">
      <header className="flex min-h-11 items-center border-b border-white/8 pb-4">
        <h1 className="text-2xl font-semibold text-white sm:text-3xl">{title}</h1>
      </header>
      {children}
    </section>
  );
}
