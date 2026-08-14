import { useEffect, useMemo, useState } from "react";
import toast from "react-hot-toast";
import { api } from "@/lib/api";
import { useTechAdminAuth } from "@/context/TechAdminAuthContext";
import { Button } from "@/components/ui/button";
import { Palette, Check, Loader2, RotateCcw, Type } from "lucide-react";

/**
 * Branding & Appearance.
 *
 * The sidebar entry pointed at TechAdminSettings, so opening it showed the
 * technical-admin profile and the MFA switch — nothing to do with branding.
 * This is the page it was supposed to open.
 *
 * Stored in the company's own settings rather than a new table: a theme is a
 * preference, and preferences already have a home.
 */

interface Theme {
  id: string;
  name: string;
  /** The one colour everything else is built around. */
  accent: string;
  surface: string;
  ink: string;
}

/**
 * Twenty looks, grouped so the list can be read rather than scrolled.
 *
 * Kept to twenty deliberately. A hundred swatches is not twenty times more
 * useful — it is a decision nobody can make, and every one of them still has to
 * stay legible against both the light and dark surfaces below.
 */
const THEMES: Theme[] = [
  { id: "indigo", name: "Indigo", accent: "#4F46E5", surface: "#FFFFFF", ink: "#0F172A" },
  { id: "royal", name: "Royal Blue", accent: "#2563EB", surface: "#FFFFFF", ink: "#0F172A" },
  { id: "sky", name: "Sky", accent: "#0284C7", surface: "#F8FAFC", ink: "#0F172A" },
  { id: "teal", name: "Teal", accent: "#0D9488", surface: "#FFFFFF", ink: "#0F172A" },
  { id: "emerald", name: "Emerald", accent: "#059669", surface: "#FFFFFF", ink: "#0F172A" },
  { id: "forest", name: "Forest", accent: "#15803D", surface: "#F7FAF7", ink: "#14261A" },
  { id: "lime", name: "Lime", accent: "#65A30D", surface: "#FCFDF7", ink: "#1A2010" },
  { id: "amber", name: "Amber", accent: "#D97706", surface: "#FFFDF7", ink: "#231607" },
  { id: "orange", name: "Orange", accent: "#EA580C", surface: "#FFFCF9", ink: "#25130A" },
  { id: "rose", name: "Rose", accent: "#E11D48", surface: "#FFFAFB", ink: "#25101A" },
  { id: "crimson", name: "Crimson", accent: "#BE123C", surface: "#FFFFFF", ink: "#1F0A12" },
  { id: "plum", name: "Plum", accent: "#9333EA", surface: "#FDFAFF", ink: "#1C1024" },
  { id: "violet", name: "Violet", accent: "#7C3AED", surface: "#FFFFFF", ink: "#160E23" },
  { id: "fuchsia", name: "Fuchsia", accent: "#C026D3", surface: "#FFFAFE", ink: "#230A26" },
  { id: "slate", name: "Slate", accent: "#475569", surface: "#F8FAFC", ink: "#0F172A" },
  { id: "graphite", name: "Graphite", accent: "#374151", surface: "#FFFFFF", ink: "#111827" },
  { id: "midnight", name: "Midnight", accent: "#6366F1", surface: "#0F172A", ink: "#E2E8F0" },
  { id: "carbon", name: "Carbon", accent: "#22D3EE", surface: "#111827", ink: "#E5E7EB" },
  { id: "obsidian", name: "Obsidian", accent: "#A78BFA", surface: "#18181B", ink: "#E4E4E7" },
  { id: "ink", name: "Deep Ink", accent: "#38BDF8", surface: "#0B1120", ink: "#DBEAFE" }
];

interface FontPair {
  id: string;
  name: string;
  /** What headings are set in. */
  heading: string;
  body: string;
}

/**
 * Twenty pairings, all from families a browser already has.
 *
 * Nothing here is downloaded. A webfont that fails to arrive falls back to
 * something the designer never saw, and on a portal people open every morning
 * that trade is not worth making for a heading.
 */
const FONTS: FontPair[] = [
  { id: "system", name: "System", heading: "system-ui, sans-serif", body: "system-ui, sans-serif" },
  { id: "grotesk", name: "Grotesk", heading: "'Segoe UI', system-ui, sans-serif", body: "'Segoe UI', system-ui, sans-serif" },
  { id: "helvetica", name: "Helvetica", heading: "Helvetica, Arial, sans-serif", body: "Helvetica, Arial, sans-serif" },
  { id: "arial", name: "Arial", heading: "Arial, sans-serif", body: "Arial, sans-serif" },
  { id: "verdana", name: "Verdana", heading: "Verdana, Geneva, sans-serif", body: "Verdana, Geneva, sans-serif" },
  { id: "tahoma", name: "Tahoma", heading: "Tahoma, Verdana, sans-serif", body: "Tahoma, Verdana, sans-serif" },
  { id: "trebuchet", name: "Trebuchet", heading: "'Trebuchet MS', sans-serif", body: "'Trebuchet MS', sans-serif" },
  { id: "calibri", name: "Calibri", heading: "Calibri, system-ui, sans-serif", body: "Calibri, system-ui, sans-serif" },
  { id: "optima", name: "Optima", heading: "Optima, Candara, sans-serif", body: "Candara, system-ui, sans-serif" },
  { id: "georgia", name: "Georgia", heading: "Georgia, serif", body: "Georgia, serif" },
  { id: "garamond", name: "Garamond", heading: "Garamond, Georgia, serif", body: "Garamond, Georgia, serif" },
  { id: "cambria", name: "Cambria", heading: "Cambria, Georgia, serif", body: "Cambria, Georgia, serif" },
  { id: "book", name: "Bookman", heading: "'Bookman Old Style', Georgia, serif", body: "Georgia, serif" },
  { id: "palatino", name: "Palatino", heading: "Palatino, 'Book Antiqua', serif", body: "Palatino, serif" },
  { id: "times", name: "Times", heading: "'Times New Roman', Times, serif", body: "'Times New Roman', serif" },
  { id: "serif-sans", name: "Serif + Sans", heading: "Georgia, serif", body: "system-ui, sans-serif" },
  { id: "sans-serif", name: "Sans + Serif", heading: "'Segoe UI', system-ui, sans-serif", body: "Georgia, serif" },
  { id: "condensed", name: "Condensed", heading: "'Arial Narrow', Arial, sans-serif", body: "Arial, sans-serif" },
  { id: "mono-head", name: "Mono Headings", heading: "'Consolas', ui-monospace, monospace", body: "system-ui, sans-serif" },
  { id: "mono", name: "Monospace", heading: "ui-monospace, 'Courier New', monospace", body: "ui-monospace, 'Courier New', monospace" }
];

interface Branding {
  themeId: string;
  fontId: string;
  productName: string;
  welcomeText: string;
}

const DEFAULTS: Branding = {
  themeId: "indigo",
  fontId: "system",
  productName: "",
  welcomeText: ""
};

export function TechAdminBranding() {
  const { theme, currentCompany } = useTechAdminAuth();
  const isDark = theme === "dark";

  const [draft, setDraft] = useState<Branding>(DEFAULTS);
  const [saved, setSaved] = useState<Branding>(DEFAULTS);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);

  const tenantId = currentCompany?.id;
  const tenantName = currentCompany?.companyName || "this company";

  const activeTheme = useMemo(
    () => THEMES.find((t) => t.id === draft.themeId) ?? THEMES[0],
    [draft.themeId]
  );
  const activeFont = useMemo(
    () => FONTS.find((f) => f.id === draft.fontId) ?? FONTS[0],
    [draft.fontId]
  );

  // Only offer Save when there is something to save.
  const dirty = JSON.stringify(draft) !== JSON.stringify(saved);

  useEffect(() => {
    if (!tenantId) return;
    let active = true;
    setLoading(true);

    (async () => {
      try {
        const res = await api.get(`/technical-admin/companies/${tenantId}/modules`);
        const rows: any[] = Array.isArray(res.data?.data) ? res.data.data : [];
        const row = rows.find((r) => r.moduleCode === "BRANDING");

        let next = DEFAULTS;
        if (row?.featureFlags) {
          try {
            const parsed = JSON.parse(row.featureFlags);
            next = { ...DEFAULTS, ...parsed };
          } catch {
            // Unreadable settings fall back to the defaults rather than
            // blanking the page.
          }
        }
        if (!active) return;
        setDraft(next);
        setSaved(next);
      } catch {
        if (active) toast.error("Could not load this company's branding");
      } finally {
        if (active) setLoading(false);
      }
    })();

    return () => {
      active = false;
    };
  }, [tenantId]);

  const save = async () => {
    if (!tenantId || saving) return;
    setSaving(true);
    try {
      /*
       * Stored as a company_modules row — the same shape custom modules use.
       *
       * No new table and no migration: the row already has a free-text JSON
       * column, and branding is exactly the kind of per-company setting it was
       * there for. Kept disabled because BRANDING is not a module anyone
       * navigates to; the row is a place to keep the settings, not a feature to
       * switch on.
       */
      await api.post(`/technical-admin/companies/${tenantId}/modules`, {
        moduleCode: "BRANDING",
        enabled: false,
        featureFlags: JSON.stringify(draft)
      });
      setSaved(draft);
      toast.success(`Saved for ${tenantName}`);
    } catch (err: any) {
      toast.error(err?.response?.data?.message || "Could not save");
    } finally {
      setSaving(false);
    }
  };

  const card = isDark
    ? "bg-slate-900/60 border border-cyan-500/25 text-slate-100"
    : "bg-white/90 border border-slate-200 text-slate-800 shadow-sm";

  if (loading) {
    return (
      <div className="flex min-h-[60vh] items-center justify-center">
        <Loader2 className="h-6 w-6 animate-spin text-slate-400" />
      </div>
    );
  }

  return (
    <div className="min-h-screen space-y-6 p-6">
      <div className="flex flex-wrap items-start justify-between gap-4">
        <div>
          <h1 className="flex items-center gap-2 text-xl font-bold">
            <Palette className="h-5 w-5" style={{ color: activeTheme.accent }} />
            Branding &amp; Appearance
          </h1>
          <p className={`mt-1 text-sm ${isDark ? "text-slate-400" : "text-slate-500"}`}>
            How the portal looks for <strong>{tenantName}</strong>. Each company keeps its own.
          </p>
        </div>

        <div className="flex items-center gap-2">
          <Button
            type="button"
            variant="outline"
            disabled={!dirty || saving}
            onClick={() => setDraft(saved)}
          >
            <RotateCcw className="mr-2 h-4 w-4" />
            Discard
          </Button>
          <Button type="button" disabled={!dirty || saving} onClick={save}>
            {saving ? <Loader2 className="mr-2 h-4 w-4 animate-spin" /> : <Check className="mr-2 h-4 w-4" />}
            {saving ? "Saving…" : dirty ? "Save changes" : "Saved"}
          </Button>
        </div>
      </div>

      <div className="grid gap-6 lg:grid-cols-[1fr_380px]">
        <div className="space-y-6">
          {/* ---- colour ---- */}
          <section className={`rounded-xl p-5 ${card}`}>
            <h2 className="text-sm font-bold uppercase tracking-wide">Colour</h2>
            <p className={`mt-1 text-xs ${isDark ? "text-slate-400" : "text-slate-500"}`}>
              Twenty to choose from. The last four are dark themes.
            </p>

            <div className="mt-4 grid grid-cols-2 gap-2 sm:grid-cols-4 lg:grid-cols-5">
              {THEMES.map((t) => {
                const picked = t.id === draft.themeId;
                return (
                  <button
                    key={t.id}
                    type="button"
                    onClick={() => setDraft((d) => ({ ...d, themeId: t.id }))}
                    aria-pressed={picked}
                    className={`rounded-lg border p-2 text-left transition ${
                      picked
                        ? "ring-2 ring-offset-1"
                        : isDark
                          ? "border-slate-700 hover:border-slate-500"
                          : "border-slate-200 hover:border-slate-400"
                    }`}
                    style={picked ? { borderColor: t.accent, boxShadow: `0 0 0 2px ${t.accent}33` } : undefined}
                  >
                    {/* The swatch shows the accent on its own surface, which is
                        the pairing that has to work — not the accent alone. */}
                    <span
                      className="flex h-9 items-center justify-center rounded-md text-[11px] font-bold"
                      style={{ background: t.surface, color: t.accent, border: `1px solid ${t.accent}44` }}
                    >
                      Aa
                    </span>
                    <span className="mt-1.5 block truncate text-[11px] font-medium">{t.name}</span>
                  </button>
                );
              })}
            </div>
          </section>

          {/* ---- type ---- */}
          <section className={`rounded-xl p-5 ${card}`}>
            <h2 className="flex items-center gap-2 text-sm font-bold uppercase tracking-wide">
              <Type className="h-4 w-4" />
              Type
            </h2>
            <p className={`mt-1 text-xs ${isDark ? "text-slate-400" : "text-slate-500"}`}>
              Families every browser already has, so nothing waits on a download.
            </p>

            <div className="mt-4 grid gap-2 sm:grid-cols-2 lg:grid-cols-3">
              {FONTS.map((f) => {
                const picked = f.id === draft.fontId;
                return (
                  <button
                    key={f.id}
                    type="button"
                    onClick={() => setDraft((d) => ({ ...d, fontId: f.id }))}
                    aria-pressed={picked}
                    className={`rounded-lg border px-3 py-2 text-left transition ${
                      picked
                        ? "ring-2 ring-offset-1"
                        : isDark
                          ? "border-slate-700 hover:border-slate-500"
                          : "border-slate-200 hover:border-slate-400"
                    }`}
                    style={picked ? { borderColor: activeTheme.accent, boxShadow: `0 0 0 2px ${activeTheme.accent}33` } : undefined}
                  >
                    {/* Set in the face it is offering, so the choice is visible
                        rather than a name to be imagined. */}
                    <span className="block text-base font-semibold" style={{ fontFamily: f.heading }}>
                      {f.name}
                    </span>
                    <span className={`block text-[11px] ${isDark ? "text-slate-400" : "text-slate-500"}`} style={{ fontFamily: f.body }}>
                      The quick brown fox
                    </span>
                  </button>
                );
              })}
            </div>
          </section>

          {/* ---- words ---- */}
          <section className={`rounded-xl p-5 ${card}`}>
            <h2 className="text-sm font-bold uppercase tracking-wide">Words</h2>
            <p className={`mt-1 text-xs ${isDark ? "text-slate-400" : "text-slate-500"}`}>
              Left empty, each falls back to the standard wording.
            </p>

            <div className="mt-4 grid gap-4 sm:grid-cols-2">
              <label className="block">
                <span className="text-xs font-semibold">Product name</span>
                <input
                  value={draft.productName}
                  onChange={(e) => setDraft((d) => ({ ...d, productName: e.target.value }))}
                  maxLength={40}
                  placeholder="Employee Management"
                  className={`mt-1 w-full rounded-lg border px-3 py-2 text-sm ${
                    isDark ? "border-slate-700 bg-slate-900 text-white" : "border-slate-300 bg-white text-slate-800"
                  }`}
                />
              </label>

              <label className="block">
                <span className="text-xs font-semibold">Welcome line</span>
                <input
                  value={draft.welcomeText}
                  onChange={(e) => setDraft((d) => ({ ...d, welcomeText: e.target.value }))}
                  maxLength={90}
                  placeholder="Here's what's happening today."
                  className={`mt-1 w-full rounded-lg border px-3 py-2 text-sm ${
                    isDark ? "border-slate-700 bg-slate-900 text-white" : "border-slate-300 bg-white text-slate-800"
                  }`}
                />
              </label>
            </div>
          </section>
        </div>

        {/* ---- preview ---- */}
        <div className="lg:sticky lg:top-6 lg:self-start">
          <section className={`rounded-xl p-5 ${card}`}>
            <h2 className="text-sm font-bold uppercase tracking-wide">Preview</h2>
            <p className={`mt-1 text-xs ${isDark ? "text-slate-400" : "text-slate-500"}`}>
              What an employee sees on opening the portal.
            </p>

            {/* Rendered with the chosen values rather than described, so the
                choice can be judged before it reaches anyone. */}
            <div
              className="mt-4 overflow-hidden rounded-xl border"
              style={{ background: activeTheme.surface, borderColor: `${activeTheme.accent}33` }}
            >
              <div className="px-4 py-3" style={{ background: activeTheme.accent }}>
                <p className="text-sm font-bold text-white" style={{ fontFamily: activeFont.heading }}>
                  {draft.productName || "Employee Management"}
                </p>
              </div>

              <div className="p-4">
                <p className="text-lg font-bold" style={{ color: activeTheme.ink, fontFamily: activeFont.heading }}>
                  Welcome, Priya
                </p>
                <p className="mt-1 text-xs" style={{ color: `${activeTheme.ink}99`, fontFamily: activeFont.body }}>
                  {draft.welcomeText || "Here's what's happening today."}
                </p>

                <div className="mt-4 grid grid-cols-2 gap-2">
                  {[
                    ["Present", "18"],
                    ["On leave", "2"]
                  ].map(([label, value]) => (
                    <div
                      key={label}
                      className="rounded-lg p-3"
                      style={{ background: `${activeTheme.accent}12`, border: `1px solid ${activeTheme.accent}22` }}
                    >
                      <p className="text-xl font-extrabold" style={{ color: activeTheme.accent, fontFamily: activeFont.heading }}>
                        {value}
                      </p>
                      <p className="text-[11px]" style={{ color: `${activeTheme.ink}99`, fontFamily: activeFont.body }}>
                        {label}
                      </p>
                    </div>
                  ))}
                </div>

                <button
                  type="button"
                  tabIndex={-1}
                  className="mt-4 w-full rounded-lg py-2 text-xs font-bold text-white"
                  style={{ background: activeTheme.accent, fontFamily: activeFont.body }}
                >
                  Punch in
                </button>
              </div>
            </div>

            {dirty && (
              <p className="mt-3 text-xs font-medium text-amber-600 dark:text-amber-400">
                Not saved yet — this is a preview.
              </p>
            )}
          </section>
        </div>
      </div>
    </div>
  );
}
