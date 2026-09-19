# 2026-06-23 — PCB/MTD income tax (completes the payroll engine)

## Why
The payroll engine computed EPF/SOCSO/EIS but deliberately left out income tax,
so net pay was overstated. This adds estimated **PCB/MTD** (monthly tax deduction)
so net pay is complete — the last documented gap in payroll.

## Honesty stance
PCB is an **estimate** and is labelled as such everywhere it appears ("PCB (est.)"),
with a caption on the staff payslip: *"PCB is an estimate — it doesn't include
year-to-date tax already paid, so your final tax may differ."* It's a real
computation from a real tax profile via the LHDN method — not a fabricated number —
but it doesn't carry YTD accumulation, so we never present it as a filed figure.

## Backend
- **Migration `V10__tax_profile_and_pcb.sql`** — `employees.marital_status`,
  `employees.spouse_working`, `employees.num_children` (tax profile); `payslips.pcb`
  (NOT NULL DEFAULT 0, so existing rows stay consistent). New `MaritalStatus` enum;
  `Employee` + `EmployeeResponse` + create/update DTOs/service carry the profile.
- **`PayrollCalculator`** PCB (pure, unit-tested to the sen): annualise basic →
  subtract EPF relief (capped RM4,000/yr) + personal reliefs (individual RM9,000;
  spouse RM4,000 when married with a non-working spouse; RM2,000/child) → YA2024
  resident brackets → ÷12. Variable OT/claims are **not** annualised; no YTD carry.
  Net now subtracts PCB.
- Run + payslip DTOs expose `pcbLabel` (per-employee + run total); the run sums a
  separate PCB total (statutory total still = EPF+SOCSO+EIS only).
- Demo seed: a few employees made married/with-children so the run shows realistic
  PCB variation.

## App
- Types: tax-profile fields on `EmployeeResponse`/create/update bodies; `pcbLabel`
  on `Payslip`/`PayrollRun`/`PayrollRunLine`; `MaritalStatus` union.
- Staff payslip — **itemised** deductions (EPF / SOCSO / EIS / PCB (est.)) so the
  rows reconcile exactly to net + the estimate caption.
- Admin payroll totals — a "PCB (est.)" row between Statutory and Net.
- Employee add/edit — a "Tax profile" group (marital status, spouse working when
  married, number of children), prefilled on edit and sent in the body.

## Bug caught in review
The first cut of the staff payslip showed `deductionsLabel` (which now *includes*
PCB) on a row labelled "Statutory · EPF, SOCSO, EIS" **and** a separate PCB row —
double-counting PCB so the breakdown didn't reconcile to net. Fixed by itemising
each deduction (EPF/SOCSO/EIS/PCB) from their own labels; the combined
`deductionsLabel` is no longer shown as a row. (Admin totals were already correct —
its statutory total excludes PCB.)

## Verification
- Backend gate: **`./scripts/test-backend.sh` → BUILD SUCCESS, 43 tests** (calculator
  unit tests extended with bracket + PCB cases: single mid-earner RM50/mo,
  married+2 children RM30/mo, working-spouse no relief, low earner RM0; `PayrollRunIT`
  updated — Amir married+2 children now net RM3,502.35 incl. PCB RM30).
- App: `npm run typecheck` clean.
- Live (dev DB reset → V10 + seeded profiles): ran July payroll — PCB matches the
  hand calcs across profiles (Amir married+2kids RM30.00; Wei Jie single RM324.17;
  Sarah married/working-spouse/1 child RM767.50; Imran married+3kids RM1,220.83;
  Faizal single RM14.76); run PCB total RM3,036.26; net reconciles.

## Follow-ups
- True LHDN MTD with YTD accumulation + prior-PCB carry (needs storing YTD figures /
  reading prior payslips) would turn the estimate into a filing-grade number.
- Additional reliefs (lifestyle, medical, etc.) and the zakat rebate aren't modelled.
- Bonus is additional-remuneration for MTD (separate formula) — not yet handled
  (bonus is 0 in current runs).
