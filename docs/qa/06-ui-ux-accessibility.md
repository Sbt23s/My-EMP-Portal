# UI/UX & Accessibility Report — Pixous HR Portal

## 1. UI/UX Review

| Area | Rating | Notes |
|---|---|---|
| Visual design | ✅ Strong | Polished glassmorphism login, consistent brand palette, dark-mode support (`next-themes`) |
| Layout/responsive | ⚠️ Good | Tailwind responsive classes throughout; tablet/mobile not physically tested in this audit |
| Loading states | ✅ | Spinners (`Loader2`), disabled buttons, skeleton patterns on dashboards |
| Empty states | ✅ | Em-dash placeholders (`—`) for missing values — thoughtful |
| Form UX | ✅ | react-hook-form + zod: inline errors, focus states |
| Toast feedback | ✅ | `react-hot-toast` for success/error |
| Icons/branding | ✅ | lucide icons; logo with graceful `onError` fallback |
| Print/export | ⚠️ | xlsx export present; PDF via backend; not visually verified |

## 2. Accessibility Review

**Score: 60/100** — functional but not systematically audited (no axe run, no keyboard-only walkthrough).

| Check | Status | Notes |
|---|---|---|
| Semantic HTML | ⚠️ | Mostly good; some `div`-as-button patterns |
| Labels | ⚠️ | Login uses `<label>` wrappers (good); audit other forms |
| Focus states | ⚠️ | Visible focus on inputs; check custom buttons |
| Keyboard nav | ⚠️ | Not tested; recommend `tab` walkthrough |
| Color contrast | ⚠️ | White-on-gradient text (login) needs verification |
| `aria-label`s | ✅ | Found on icon-only buttons (e.g. password toggle) |
| Reduced motion | ⚠️ | framer-motion animations; consider `prefers-reduced-motion` |
| Alt text | ⚠️ | Logo has alt; scan other images |

## 3. Findings

- **QA-011 (Low):** Some interactive elements are buttons rendered as styled `div`s — switch to `<button>` or add role/tabindex/keydown.
- **QA-012 (Info):** No automated a11y tests. **Cheap fix:** add `eslint-plugin-jsx-a11y`; run `axe-core` once against the 10 most-used pages; fix the top contrast/focus issues.

## 4. Recommendation

A 1-day a11y pass (axe + keyboard walkthrough + contrast fixes) would move the score to ~80. Accessibility is not blocking for an internal HR tool but matters for compliance (e.g., EU/enterprise procurement).
