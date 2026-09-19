# 2026-06-24 — Payslip PDF download

## Why
The staff Payslip screen had an inert "Download payslip (PDF)" button — a visible
dead end. Now it produces a real, branded PDF and opens the native share sheet
(save to Files, email, AirDrop, etc.).

## How
Client-side, no backend change — the payslip data is already on the device.
- **`src/lib/payslipHtml.ts`** (pure, no native deps → renderable/testable in
  isolation) — `payslipHtml(payslip, employee, date)` builds a branded "Warm &
  Human" HTML document (company header, employee + pay date, an itemised earnings
  table → gross, a deductions table EPF/SOCSO/EIS/PCB → total, net pay, and the
  PCB-estimate footnote). The HTML hard-codes the brand hex — it's a document, not
  an RN component, so it can't pull from `@/theme`.
- **`src/lib/payslipPdf.ts`** — `sharePayslipPdf(...)` renders that HTML with
  `expo-print` (`printToFileAsync`) and opens `expo-sharing`. Both work in Expo Go
  and dev/EAS builds (unlike push).
- **`app/(staff)/(tabs)/payroll.tsx`** — the button now has a busy state
  ("Preparing…"), calls `sharePayslipPdf` with the shown payslip + `useMe()` for the
  employee/company identity, and shows an Alert on failure.
- Gross isn't in the payslip DTO, so it's derived in the document from the earnings
  line items (basic + OT + claims + bonus) via a small money parser/formatter (no
  Intl dependency).
- Added deps via `npx expo install`: `expo-print`, `expo-sharing`. No config plugin
  needed.

## Verification
- `npm run typecheck` clean.
- **Rendered the actual document**: esbuild-bundled the pure `payslipHtml` with a
  sample payslip → HTML → PDF via headless Chrome, and inspected the result. It's
  on-design (cream/ink/coral/sage), correctly itemised, and the math reconciles
  (gross 4,108.00 = 4000+108; deductions 497.65 = 440+19.75+7.90+30; net 3,502.35);
  the PCB-estimate footnote is present.

## Follow-ups
- **On-device smoke**: the document is verified via Chrome's HTML→PDF; a quick
  check on a simulator confirms `expo-print`'s engine lays it out identically
  (it uses the platform WebView, so it should).
- The screen still shows only the latest payslip; a payslip history/picker (and
  per-period download) would be a natural next step.
- An "email me my payslip" server path (vs. share sheet) could be added later.
