export function StatusMessage({
  tone,
  children,
}: {
  tone: "error" | "info";
  children: string;
}) {
  return (
    <div
      className={[
        "rounded-lg border px-4 py-3 text-sm",
        tone === "error"
          ? "border-rose-400/25 bg-rose-500/10 text-rose-200"
          : "border-brand-400/20 bg-brand-500/10 text-brand-100",
      ].join(" ")}
    >
      {children}
    </div>
  );
}

