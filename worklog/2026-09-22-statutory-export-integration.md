# 2026-09-22 — Statutory reporting & export: integration + verification

Backend (V14 employee statutory/bank fields + export service) and frontend
(Statutory & bank form section + Payroll → Reports & export screen) built by
subagents. This entry covers integration.

## Verified (dev, end-to-end via API)
- Backend gate `./scripts/test-backend.sh` → BUILD SUCCESS, 75 tests (6 new
  PayrollExportIT + CsvUtilTest + Cp39Test).
- V14 applied to dev DB via fast jar hot-swap (6 employee columns: nric, epf_no,
  socso_no, tax_no, bank_name, bank_account_no).
- Ran payroll for 2026-09, then:
  - `GET /api/admin/payroll/export/summary` → per-employee rows + company totals
    (employer EPF/SOCSO/EIS derived from stored basic+bonus via the same
    PayrollCalculator.statutory used at run time).
  - `GET .../export/file?type=` for contributions / bank / payroll (CSV) + cp39
    (text) → all 200 with correct filenames, mime types, header rows. CP39 header
    `CP39|CP39-TXT-v1|Lumi Foods Sdn Bhd|09|2026`.
  - RBAC: MANAGER → 403; unauthenticated → 401; no-run period → 400.
- Frontend `npm run typecheck` clean; contract matched (backend adds an extra
  `periodLabel` the app can ignore/use — no breakage).

## Honesty / scope
- CP39 is best-effort (`CP39-TXT-v1`), labeled "review before submission" in the
  UI; PCB stays the monthly estimate (no YTD). Byte-exact EPF e-Caruman /
  PERKESO ASSIST / bank IBG formats deferred to v2 pending verified specs.

## Batch status (ONE preview build pending owner go)
Native modules now queued: expo-location (geofence) + expo-camera (selfie) +
expo-file-system (export). All three integrated + verified on dev; typecheck clean.
Backend V11–V14 live on dev via jar hot-swaps.
