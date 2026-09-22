# 2026-09-22 — Statutory reporting & export (backend)

## What
Added per-period statutory reporting and file export for admins (OWNER/HR_ADMIN),
plus the employee statutory & bank identity fields that make a real export possible.

### Data model
- **Migration `V14__employee_statutory_bank.sql`** — adds six nullable columns to
  `employees`: `nric`, `epf_no`, `socso_no`, `tax_no`, `bank_name`, `bank_account_no`.
  All optional; exports leave a blank cell when unset (honest empty state, never faked).
- `Employee` entity + `EmployeeResponse` + `Create/UpdateEmployeeRequest` DTOs +
  `EmployeeService` create/update threaded the six fields through exactly like the V11
  compensation fields (blank → null on write via a `trimToNull` helper). Detail + list
  responses now include them.

### Export engine (`com.teamora.payroll.export`)
- `PayrollExportService` reads the **persisted** payslips for a period (company-scoped,
  employee fetch-joined) — no recompute. Employee-side EPF/SOCSO/EIS/PCB/net come straight
  from the stored payslip. Employer-side EPF/SOCSO/EIS are **not** persisted anywhere, so
  they're derived deterministically from the stored `basic + bonus` via the existing pure
  `PayrollCalculator.statutory(...)` (the same function used at run time) — consistent with
  the stored employee side. Totals are the exact sum of the rows.
- `CsvUtil` — tiny RFC-4180-ish writer (quote only when a field has `, " CR LF`; double
  embedded quotes; CRLF row terminator). Money uses `PayslipFormat.money` (grouped), so a
  cell like `4,000.00` is correctly quoted by the writer.
- `Cp39Generator` — best-effort LHDN CP39 (PCB) fixed-width text, **layout version
  `CP39-TXT-v1`** (documented in a header comment). One 92-char detail line per employee
  (tax no 12 / NRIC digits 12 / name 40 / PCB sen 11 / CP38 sen 11 / MM 2 / YYYY 4) after a
  header line. Explicitly NOT the official e-CP39 CSV/XML (deferred to v2); the app shows a
  "review before submission" disclaimer, and PCB remains the monthly estimate (no YTD).

### Endpoints (OWNER/HR_ADMIN only via `@PreAuthorize`, MANAGER excluded — matches payroll-run)
- `GET /api/admin/payroll/export/summary?period=YYYY-MM` → `StatutorySummaryResponse`
  (`period`, `periodLabel`, `generatedAtLabel`, `rows[]`, `totals`). All money pre-formatted
  server-side as `*Label`s.
- `GET /api/admin/payroll/export/file?period=YYYY-MM&type=contributions|bank|payroll|cp39`
  → `ExportFileResponse { filename, mimeType, content }`.
- No run for the period → **400** with a clear message; unknown `type` → 400.

## Tests (gate: `./scripts/test-backend.sh` → BUILD SUCCESS, 75 tests)
- `PayrollExportIT` — HR runs payroll, then: summary totals == sum of rows (to the sen) and
  Amir's identity surfaces; each file type returns non-empty content + correct
  filename/mimeType/header; no-run → 400; unknown type → 400; MANAGER → 403; unauthenticated → 401.
- `CsvUtilTest` — escaping/quoting/CRLF.
- `Cp39Test` — pins the fixed-layout positions for a known employee/amount + sen conversion.

## Notes / decisions
- **Employer contributions are derived, not stored.** Documented in `PayrollExportService`
  and `StatutorySummaryResponse`. This is the only sensible source given the payslip persists
  only the employee side; it stays consistent because it reuses the run-time calculator on the
  stored basic+bonus.
- Scope was backend-only per the task; no `app/` or `src/` changes. The app-side wiring
  (`payrollExportApi`, export screen, employee form section) from the spec is a follow-up.
- V14 ships via the jar hot-swap; the running dev docker stack was **not** rebuilt/disrupted
  (tests ran against the throwaway `teamora_test` DB).
