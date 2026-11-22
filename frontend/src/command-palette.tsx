import { useEffect, useMemo, useRef, useState } from "react";
import { useNavigate } from "react-router-dom";
import { Search, CornerDownLeft, type LucideIcon } from "lucide-react";

export type CommandItem = {
  to: string;
  label: string;
  section: string;
  icon: LucideIcon;
};

export function CommandPalette({
  items,
  open,
  onClose,
}: {
  items: CommandItem[];
  open: boolean;
  onClose: () => void;
}) {
  const [query, setQuery] = useState("");
  const [index, setIndex] = useState(0);
  const inputRef = useRef<HTMLInputElement>(null);
  const navigate = useNavigate();

  const filtered = useMemo(() => {
    const q = query.trim().toLowerCase();
    if (!q) return items;
    return items.filter(
      (i) =>
        i.label.toLowerCase().includes(q) ||
        i.section.toLowerCase().includes(q) ||
        i.to.toLowerCase().includes(q),
    );
  }, [items, query]);

  useEffect(() => {
    if (open) {
      setQuery("");
      setIndex(0);
      setTimeout(() => inputRef.current?.focus(), 10);
    }
  }, [open]);

  useEffect(() => {
    setIndex(0);
  }, [query]);

  useEffect(() => {
    if (!open) return;
    const onKey = (e: KeyboardEvent) => {
      if (e.key === "Escape") {
        e.preventDefault();
        onClose();
      } else if (e.key === "ArrowDown") {
        e.preventDefault();
        setIndex((i) => (filtered.length ? (i + 1) % filtered.length : 0));
      } else if (e.key === "ArrowUp") {
        e.preventDefault();
        setIndex((i) =>
          filtered.length ? (i - 1 + filtered.length) % filtered.length : 0,
        );
      } else if (e.key === "Enter" && filtered[index]) {
        e.preventDefault();
        navigate(filtered[index].to);
        onClose();
      }
    };
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, [open, filtered, index, navigate, onClose]);

  if (!open) return null;

  return (
    <div
      className="cmd-backdrop"
      onClick={onClose}
      role="dialog"
      aria-modal="true"
      aria-label="Quick navigation"
    >
      <div className="cmd-modal" onClick={(e) => e.stopPropagation()}>
        <div className="cmd-input-row">
          <Search size={16} />
          <input
            ref={inputRef}
            value={query}
            onChange={(e) => setQuery(e.target.value)}
            placeholder="Jump to module, screen, or action…"
            aria-label="Search navigation"
          />
          <kbd>ESC</kbd>
        </div>
        <div className="cmd-list">
          {filtered.length === 0 ? (
            <div className="cmd-empty">No matching screens found.</div>
          ) : (
            filtered.map((item, i) => {
              const Icon = item.icon;
              return (
                <button
                  key={item.to}
                  type="button"
                  className={`cmd-item ${i === index ? "active" : ""}`}
                  onMouseEnter={() => setIndex(i)}
                  onClick={() => {
                    navigate(item.to);
                    onClose();
                  }}
                >
                  <span className="cmd-item-left">
                    <Icon size={15} />
                    <strong>{item.label}</strong>
                    <span className="cmd-section">{item.section}</span>
                  </span>
                  {i === index && <CornerDownLeft size={14} />}
                </button>
              );
            })
          )}
        </div>
      </div>
    </div>
  );
}
