import React, { useCallback, useEffect, useMemo, useState } from 'react';
import { Share } from 'react-native';
import { useQuery } from '@tanstack/react-query';
import { useRouter } from 'expo-router';
import { AsyncBoundary } from '@/components/layout/AsyncBoundary';
import { fromHHMM, toHHMM, toISODate } from '@/components/ui';
import { WizardCelebration, type CelebrationItem, type WizardStep } from '@/components/wizard';
import { ApiError } from '@/api/client';
import { calendarAdminApi, companyApi, companySettingsApi, leaveTypeApi, workLocationApi } from '@/api/endpoints';
import {
  qk,
  useCompleteCompanySetup,
  useCreateEmployee,
  useCreateWorkLocation,
  useImportHolidays,
  useUpdateCompany,
  useUpdateCompanySettings,
  useUpdateLeaveType,
  useUpdateWorkLocation,
} from '@/api/queries';
import type { CompanySettings, HolidaySuggestion } from '@/api/types';
import { useAuth } from '@/context/AuthContext';
import { errorMessage } from '@/lib/errors';
import { defaultHolidayTicks } from '@/lib/holidays';
import { getCurrentCoords, LocationError } from '@/lib/location';
import { passwordError } from '@/lib/password';
import { generateTempPassword } from '@/lib/tempPassword';
import { WEEKDAYS_MASK, describeMask, isEmptyMask } from '@/lib/workweek';
import { palette, tint } from '@/theme';
import { CompanyStep } from './CompanyStep';
import { HolidaysStep, type HolidayGroup } from './HolidaysStep';
import { LeaveStep } from './LeaveStep';
import { TeamStep } from './TeamStep';
import { WelcomeMark } from './WelcomeMark';
import { WelcomeStep } from './WelcomeStep';
import { GRACE_MAX, HOURS_MAX, HOURS_MIN, WorkWeekStep } from './WorkWeekStep';
import { RADIUS_MAX, RADIUS_MIN, WorkplaceStep } from './WorkplaceStep';
import { YouStep } from './YouStep';
import type {
  AccountErrors,
  AccountForm,
  CompanyErrors,
  CompanyForm,
  InviteRow,
  SetupMode,
  WorkWeekForm,
  WorkplaceErrors,
  WorkplaceForm,
} from './types';

const EMAIL_RE = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
const DEFAULT_RADIUS_M = 200;

function clamp(n: number, min: number, max: number): number {
  return Math.min(max, Math.max(min, n));
}

/** Server settings → the work-week form (falling back to the usual 9-to-5, Mon–Fri). */
function workWeekFrom(s: CompanySettings): WorkWeekForm {
  const start = fromHHMM(s.workStartTime) ?? fromHHMM('09:00')!;
  const hours = Number(s.defaultHoursPerDay);
  return {
    days: isEmptyMask(s.defaultWorkingDays) ? WEEKDAYS_MASK : s.defaultWorkingDays,
    hours: Number.isFinite(hours) ? clamp(Math.round(hours * 2) / 2, HOURS_MIN, HOURS_MAX) : 8,
    start,
    grace: clamp(Math.round((s.lateGraceMinutes ?? 5) / 5) * 5, 0, GRACE_MAX),
  };
}

let rowSeq = 0;
function blankRow(): InviteRow {
  rowSeq += 1;
  return { id: `row-${rowSeq}`, fullName: '', email: '', role: 'EMPLOYEE', status: 'draft' };
}

const plural = (n: number, one: string, many: string) => `${n} ${n === 1 ? one : many}`;

/**
 * The new-company setup flow as Wizard steps — shared by the signed-out route
 * (`/setup`, mode `new`) and the owner's resume route (`/admin/setup`, mode
 * `resume`, which starts at the work week).
 *
 * All form state lives here so it survives moving back and forth between
 * steps. Each step saves to the server on Continue, so quitting midway loses
 * nothing: the dashboard's "Finish setting up" card reopens the flow with
 * everything prefilled from what was saved.
 *
 * `accountCreated` flips once step 3 has created the company + owner; from then
 * on there is no going back to steps 1–3 and the admin queries switch on.
 */
export function useCompanySetupSteps(mode: SetupMode): { steps: WizardStep[]; accountCreated: boolean } {
  const router = useRouter();
  const { signUp, status } = useAuth();

  // ── 2–3. You + company ─────────────────────────────────────────────────
  const [account, setAccount] = useState<AccountForm>({ fullName: '', email: '', password: '' });
  const [accountErrors, setAccountErrors] = useState<AccountErrors>({});
  const [company, setCompany] = useState<CompanyForm>({ companyName: '', registrationNo: '' });
  const [companyErrors, setCompanyErrors] = useState<CompanyErrors>({});
  const [accountCreated, setAccountCreated] = useState(mode === 'resume');

  // Admin data is only requested once there's a session — an unauthenticated
  // call would 401 and trip the client's sign-out handler.
  const authed = status === 'authenticated' && accountCreated;

  const settingsQ = useQuery({ queryKey: qk.companySettings, queryFn: companySettingsApi.get, enabled: authed });
  const leaveTypesQ = useQuery({ queryKey: qk.adminLeaveTypes, queryFn: leaveTypeApi.all, enabled: authed });
  const locationsQ = useQuery({ queryKey: qk.adminWorkLocations, queryFn: workLocationApi.adminList, enabled: authed });
  const companyQ = useQuery({ queryKey: qk.company, queryFn: companyApi.me, enabled: authed });

  const updateCompany = useUpdateCompany();
  const updateSettings = useUpdateCompanySettings();
  const updateLeaveType = useUpdateLeaveType();
  const importHolidays = useImportHolidays();
  const createLocation = useCreateWorkLocation();
  const updateLocation = useUpdateWorkLocation();
  const createEmployee = useCreateEmployee();
  const completeSetup = useCompleteCompanySetup();

  const companyName = (mode === 'new' ? company.companyName.trim() : companyQ.data?.name) || companyQ.data?.name || '';

  // ── 4. Work week ───────────────────────────────────────────────────────
  const [workWeek, setWorkWeek] = useState<WorkWeekForm | null>(null);
  const [workWeekSaved, setWorkWeekSaved] = useState(false);
  useEffect(() => {
    if (!workWeek && settingsQ.data) setWorkWeek(workWeekFrom(settingsQ.data));
  }, [workWeek, settingsQ.data]);

  // ── 5. Leave ───────────────────────────────────────────────────────────
  const activeTypes = useMemo(
    () => (leaveTypesQ.data ?? []).filter((t) => t.active).sort((a, b) => a.sortOrder - b.sortOrder),
    [leaveTypesQ.data],
  );
  const trackedTypes = useMemo(() => activeTypes.filter((t) => t.accrual !== 'NONE'), [activeTypes]);
  const untrackedTypes = useMemo(() => activeTypes.filter((t) => t.accrual === 'NONE'), [activeTypes]);
  const [leaveDays, setLeaveDays] = useState<Record<string, number>>({});
  const [leaveSaved, setLeaveSaved] = useState(false);

  // ── 6. Public holidays ─────────────────────────────────────────────────
  // Dates still ahead: the rest of this year and — from October, when the
  // catalogue has it — next year too.
  const today = useMemo(() => new Date(), []);
  const year = today.getFullYear();
  const todayIso = toISODate(today);
  const thisYearQ = useQuery({
    queryKey: qk.holidaySuggestions(year),
    queryFn: () => calendarAdminApi.holidaySuggestions(year),
    enabled: authed,
  });
  const wantNextYear = today.getMonth() >= 9 && !!thisYearQ.data?.years?.includes(year + 1);
  const nextYearQ = useQuery({
    queryKey: qk.holidaySuggestions(year + 1),
    queryFn: () => calendarAdminApi.holidaySuggestions(year + 1),
    enabled: authed && wantNextYear,
  });
  const holidaysReady = !!thisYearQ.data && (!wantNextYear || !!nextYearQ.data);
  const holidayGroups = useMemo<HolidayGroup[]>(() => {
    if (!thisYearQ.data) return [];
    const rest = thisYearQ.data.items.filter((h) => h.date >= todayIso);
    const groups: HolidayGroup[] = [{ title: wantNextYear ? `Rest of ${year}` : String(year), items: rest }];
    if (wantNextYear && nextYearQ.data) groups.push({ title: String(year + 1), items: nextYearQ.data.items });
    return groups;
  }, [thisYearQ.data, nextYearQ.data, wantNextYear, todayIso, year]);
  const holidayItems = useMemo<HolidaySuggestion[]>(() => holidayGroups.flatMap((g) => g.items), [holidayGroups]);
  const [ticks, setTicks] = useState<Set<string> | null>(null);
  useEffect(() => {
    if (!ticks && holidaysReady) setTicks(new Set(defaultHolidayTicks(holidayItems)));
  }, [ticks, holidaysReady, holidayItems]);
  const chosenHolidays = holidayItems.filter((h) => !h.alreadyAdded && ticks?.has(h.date));
  const holidaysOnCalendar = holidayItems.filter((h) => h.alreadyAdded).length;
  const [holidaysAdded, setHolidaysAdded] = useState(0);

  // ── 7. Workplace ───────────────────────────────────────────────────────
  const [workplace, setWorkplace] = useState<WorkplaceForm>({ choice: null, name: '', coords: null, radiusM: DEFAULT_RADIUS_M });
  const [workplaceErrors, setWorkplaceErrors] = useState<WorkplaceErrors>({});
  const [locating, setLocating] = useState(false);
  /** The site this wizard created — a second pass updates it instead of adding a twin. */
  const [siteId, setSiteId] = useState<string | null>(null);
  const [workplaceSaved, setWorkplaceSaved] = useState<'site' | 'anywhere' | null>(null);
  const existingSites = useMemo(
    () => (locationsQ.data ?? []).filter((l) => l.active && l.id !== siteId).map((l) => l.name),
    [locationsQ.data, siteId],
  );

  const captureLocation = useCallback(async () => {
    setWorkplaceErrors((e) => ({ ...e, coords: undefined }));
    setLocating(true);
    try {
      const coords = await getCurrentCoords();
      setWorkplace((w) => ({ ...w, coords }));
    } catch (e) {
      setWorkplaceErrors((prev) => ({
        ...prev,
        coords:
          e instanceof LocationError && e.kind === 'denied'
            ? 'Location permission is off. Allow location for Teamora in Settings, then try again.'
            : "Couldn't get your location. Move somewhere open and try again.",
      }));
    } finally {
      setLocating(false);
    }
  }, []);

  // ── 8. Team ────────────────────────────────────────────────────────────
  const [rows, setRows] = useState<InviteRow[]>(() => [blankRow()]);
  const [inviting, setInviting] = useState(false);
  const pendingRows = rows.filter((r) => r.status === 'draft' && (r.fullName.trim() || r.email.trim()));
  const invited = rows.filter((r) => r.status === 'created').length;

  const shareLogin = useCallback(
    (row: InviteRow) => {
      const message =
        `Hi ${row.fullName.trim()}, you've been added to ${companyName || 'our company'} on Teamora. ` +
        `Download the app and sign in with ${row.email.trim()} / temporary password ${row.password}. ` +
        'You can change it in Profile → Change password.';
      Share.share({ message }).catch(() => {});
    },
    [companyName],
  );

  // ── Steps ──────────────────────────────────────────────────────────────
  const accountSteps: WizardStep[] = [
    {
      key: 'welcome',
      title: 'Welcome to Teamora',
      subtitle: "Let's set up your company — it takes about 3 minutes.",
      hero: <WelcomeMark />,
      hideProgress: true,
      primaryLabel: 'Get started',
      render: () => <WelcomeStep onSignIn={() => router.replace('/login')} />,
    },
    {
      key: 'you',
      title: 'First, about you',
      subtitle: "You'll be the owner of your company's Teamora account.",
      icon: 'user',
      render: () => (
        <YouStep
          form={account}
          errors={accountErrors}
          onChange={(patch) => {
            setAccount((a) => ({ ...a, ...patch }));
            // Clear a field's error as soon as it's edited.
            setAccountErrors((e) => {
              const next = { ...e };
              (Object.keys(patch) as (keyof AccountForm)[]).forEach((k) => delete next[k]);
              return next;
            });
          }}
        />
      ),
      onContinue: async () => {
        const errors: AccountErrors = {};
        if (!account.fullName.trim()) errors.fullName = 'Enter your name.';
        if (!account.email.trim()) errors.email = 'Enter your work email.';
        else if (!EMAIL_RE.test(account.email.trim())) errors.email = "That doesn't look like an email address.";
        const rule = passwordError(account.password);
        if (rule) errors.password = rule;
        setAccountErrors(errors);
        return Object.keys(errors).length === 0;
      },
    },
    {
      key: 'company',
      title: 'Your company',
      subtitle: 'This creates your company on Teamora.',
      icon: 'building',
      iconTone: { color: palette.amber, bg: tint.amber },
      // Once the account exists, steps 1–3 are behind us.
      noBack: accountCreated,
      primaryLabel: accountCreated ? 'Continue' : 'Create company',
      render: () => (
        <CompanyStep
          form={company}
          errors={companyErrors}
          locked={accountCreated}
          onChange={(patch) => {
            setCompany((c) => ({ ...c, ...patch }));
            setCompanyErrors({});
          }}
          onOpenTerms={() => router.push('/terms')}
          onOpenPrivacy={() => router.push('/privacy')}
        />
      ),
      onContinue: async (ctx) => {
        const name = company.companyName.trim();
        if (!name) {
          setCompanyErrors({ companyName: 'Enter your company name.' });
          return false;
        }
        if (!accountCreated) {
          try {
            // Signs in as the new owner. Nothing redirects on sign-in (see
            // AuthContext.signUp), so the wizard simply carries on.
            await signUp(name, account.fullName.trim(), account.email.trim(), account.password, { deferPush: true });
            setAccountCreated(true);
          } catch (e) {
            if (e instanceof ApiError) {
              const fields = e.body?.fieldErrors ?? {};
              const emailTaken = e.status === 400 && /email/i.test(e.message);
              if (fields.email || fields.fullName || fields.password || emailTaken) {
                // The problem is on the previous step — take them there, error in place.
                setAccountErrors({
                  email: fields.email ?? (emailTaken ? `${e.message}. Use a different email, or sign in instead.` : undefined),
                  fullName: fields.fullName,
                  password: fields.password,
                });
                ctx.goTo('you');
                return false;
              }
              if (fields.companyName) {
                setCompanyErrors({ companyName: fields.companyName });
                return false;
              }
            }
            throw e;
          }
        }
        const registrationNo = company.registrationNo.trim();
        if (registrationNo) {
          try {
            await updateCompany.mutateAsync({ registrationNo });
          } catch (e) {
            // The company exists; only this optional field failed. Say so, in place.
            setCompanyErrors({
              registrationNo: `Your company is created, but this wasn't saved: ${errorMessage(e)} Try again, or clear it and add it later.`,
            });
            return false;
          }
        }
      },
    },
  ];

  const setupSteps: WizardStep[] = [
    {
      key: 'workweek',
      title: 'Your work week',
      subtitle: 'When does your team usually work?',
      icon: 'calendar',
      noBack: mode === 'new',
      canContinue: !!workWeek && !isEmptyMask(workWeek.days),
      render: () => (
        <AsyncBoundary loading={settingsQ.isLoading || (!workWeek && !settingsQ.error)} error={settingsQ.error} onRetry={settingsQ.refetch}>
          {workWeek && <WorkWeekStep form={workWeek} onChange={(patch) => setWorkWeek((w) => (w ? { ...w, ...patch } : w))} />}
        </AsyncBoundary>
      ),
      onContinue: async () => {
        if (!workWeek) return false;
        await updateSettings.mutateAsync({
          defaultWorkingDays: workWeek.days,
          defaultHoursPerDay: workWeek.hours,
          workStartTime: toHHMM(workWeek.start),
          lateGraceMinutes: workWeek.grace,
        });
        setWorkWeekSaved(true);
      },
    },
    {
      key: 'leave',
      title: 'Leave your team gets',
      subtitle: 'How many days a year for each type?',
      icon: 'sun',
      iconTone: { color: palette.amber, bg: tint.amber },
      canContinue: !!leaveTypesQ.data,
      render: () => (
        <AsyncBoundary loading={leaveTypesQ.isLoading} error={leaveTypesQ.error} onRetry={leaveTypesQ.refetch}>
          {leaveTypesQ.data && (
            <LeaveStep
              tracked={trackedTypes}
              untracked={untrackedTypes}
              days={leaveDays}
              onChange={(id, value) => setLeaveDays((d) => ({ ...d, [id]: value }))}
            />
          )}
        </AsyncBoundary>
      ),
      onContinue: async () => {
        const changed = trackedTypes.filter((t) => leaveDays[t.id] != null && leaveDays[t.id] !== t.defaultEntitlementDays);
        for (const t of changed) {
          await updateLeaveType.mutateAsync({ id: t.id, body: { defaultEntitlementDays: leaveDays[t.id] } });
        }
        setLeaveSaved(true);
      },
    },
    {
      key: 'holidays',
      title: 'Public holidays',
      subtitle: 'Tick the ones your company observes.',
      icon: 'gift',
      iconTone: { color: palette.sage, bg: tint.sage },
      optional: true,
      canContinue: holidaysReady && !!ticks,
      primaryLabel: chosenHolidays.length > 0 ? `Add ${plural(chosenHolidays.length, 'holiday', 'holidays')}` : 'Continue',
      render: () => (
        <AsyncBoundary
          loading={thisYearQ.isLoading || nextYearQ.isLoading || (!ticks && !thisYearQ.error && !nextYearQ.error)}
          error={thisYearQ.error ?? nextYearQ.error}
          onRetry={() => {
            void thisYearQ.refetch();
            if (wantNextYear) void nextYearQ.refetch();
          }}
        >
          {ticks && (
            <HolidaysStep
              groups={holidayGroups}
              ticks={ticks}
              source={thisYearQ.data?.source}
              onToggle={(date) =>
                setTicks((prev) => {
                  const next = new Set(prev ?? []);
                  if (next.has(date)) next.delete(date);
                  else next.add(date);
                  return next;
                })
              }
            />
          )}
        </AsyncBoundary>
      ),
      onContinue: async () => {
        if (chosenHolidays.length === 0) return;
        const res = await importHolidays.mutateAsync(chosenHolidays.map((h) => ({ date: h.date, name: h.name })));
        setHolidaysAdded((n) => n + (res?.created ?? chosenHolidays.length));
      },
    },
    {
      key: 'workplace',
      title: 'Where does your team clock in?',
      subtitle: 'Teamora can check that staff are at work when they clock in.',
      icon: 'pin',
      iconTone: { color: palette.violet, bg: tint.violet },
      optional: true,
      canContinue: workplace.choice !== null,
      render: () => (
        <WorkplaceStep
          form={workplace}
          errors={workplaceErrors}
          locating={locating}
          onUseCurrentLocation={() => void captureLocation()}
          existingSites={existingSites}
          onChange={(patch) => {
            setWorkplace((w) => ({ ...w, ...patch }));
            if (patch.name != null) setWorkplaceErrors((e) => ({ ...e, name: undefined }));
          }}
        />
      ),
      onContinue: async () => {
        if (workplace.choice === 'anywhere') {
          // Changed their mind after creating a site here → switch it off again.
          if (siteId) await updateLocation.mutateAsync({ id: siteId, body: { active: false } });
          setWorkplaceSaved('anywhere');
          return;
        }
        const errors: WorkplaceErrors = {};
        if (!workplace.name.trim()) errors.name = 'Give the site a name.';
        if (!workplace.coords) errors.coords = 'Capture the site\'s location with "Use my current location".';
        setWorkplaceErrors(errors);
        if (errors.name || errors.coords || !workplace.coords) return false;
        const body = {
          name: workplace.name.trim(),
          latitude: workplace.coords.latitude,
          longitude: workplace.coords.longitude,
          radiusM: clamp(workplace.radiusM, RADIUS_MIN, RADIUS_MAX),
          active: true,
        };
        if (siteId) {
          await updateLocation.mutateAsync({ id: siteId, body });
        } else {
          const created = await createLocation.mutateAsync(body);
          setSiteId(created.id);
        }
        setWorkplaceSaved('site');
      },
    },
    {
      key: 'team',
      title: 'Invite your team',
      subtitle: 'Add a few people now, or do it later from Staff.',
      icon: 'users',
      iconTone: { color: palette.sage, bg: tint.sage },
      optional: true,
      primaryLabel: pendingRows.length > 0 ? `Add ${plural(pendingRows.length, 'person', 'people')}` : 'Continue',
      render: () => (
        <TeamStep
          rows={rows}
          busy={inviting}
          onShare={shareLogin}
          onAddRow={() => setRows((r) => [...r, blankRow()])}
          onRemoveRow={(id) => setRows((r) => r.filter((x) => x.id !== id))}
          onChangeRow={(id, patch) => setRows((r) => r.map((x) => (x.id === id ? { ...x, ...patch, error: null } : x)))}
        />
      ),
      onContinue: async () => {
        if (pendingRows.length === 0) return; // nothing new to add → move on
        // Validate every pending row first, so one tap shows everything to fix.
        const seen = new Set(rows.filter((r) => r.status === 'created').map((r) => r.email.trim().toLowerCase()));
        const problems = new Map<string, string>();
        for (const r of pendingRows) {
          const email = r.email.trim().toLowerCase();
          if (!r.fullName.trim()) problems.set(r.id, 'Enter their name.');
          else if (!EMAIL_RE.test(email)) problems.set(r.id, 'Enter a valid work email.');
          else if (seen.has(email)) problems.set(r.id, "You've already added this email.");
          seen.add(email);
        }
        if (problems.size > 0) {
          setRows((all) => all.map((r) => (problems.has(r.id) ? { ...r, error: problems.get(r.id) } : r)));
          return false;
        }
        setInviting(true);
        try {
          const joinDate = toISODate(new Date());
          for (const r of pendingRows) {
            const password = generateTempPassword();
            try {
              await createEmployee.mutateAsync({
                fullName: r.fullName.trim(),
                email: r.email.trim(),
                password,
                role: r.role,
                joinDate,
                // Only when "One office or site" was set up here — otherwise no location check.
                ...(workplaceSaved === 'site' && siteId ? { workLocationId: siteId } : {}),
              });
              setRows((all) => all.map((x) => (x.id === r.id ? { ...x, status: 'created', password, error: null } : x)));
            } catch (e) {
              setRows((all) => all.map((x) => (x.id === r.id ? { ...x, error: errorMessage(e) } : x)));
            }
          }
          // Drop untouched blank cards; keep created people and any that failed.
          setRows((all) => all.filter((x) => x.status === 'created' || x.fullName.trim() || x.email.trim()));
        } finally {
          setInviting(false);
        }
        // Stay here: each new person now shows their temporary password + "Share login".
        return false;
      },
    },
    {
      key: 'done',
      title: "You're all set",
      bare: true,
      primaryLabel: 'Go to dashboard',
      render: () => {
        const holidaysDone = holidaysAdded > 0 || holidaysOnCalendar > 0;
        const items: CelebrationItem[] = [
          { label: 'Company created', done: true },
          { label: 'Work week', done: workWeekSaved, detail: workWeekSaved && workWeek ? describeMask(workWeek.days) : undefined },
          { label: 'Leave', done: leaveSaved, detail: leaveSaved ? plural(trackedTypes.length, 'type', 'types') : undefined },
          {
            label: 'Public holidays',
            done: holidaysDone,
            detail: holidaysAdded > 0 ? `${holidaysAdded} added` : holidaysDone ? 'On your calendar' : 'Skipped',
          },
          {
            label: 'Workplace',
            done: workplaceSaved !== null || existingSites.length > 0,
            detail:
              workplaceSaved === 'site'
                ? workplace.name.trim()
                : workplaceSaved === 'anywhere'
                  ? 'Anywhere'
                  : existingSites.length > 0
                    ? plural(existingSites.length, 'site', 'sites')
                    : 'Skipped',
          },
          { label: 'Team', done: invited > 0, detail: invited > 0 ? `${invited} invited` : 'Skipped' },
        ];
        return (
          <WizardCelebration
            title="You're all set"
            subtitle={
              companyName
                ? `${companyName} is ready to go. You can change any of this later from Profile.`
                : 'You can change any of this later from Profile.'
            }
            items={items}
          />
        );
      },
      onContinue: async () => {
        await completeSetup.mutateAsync();
      },
    },
  ];

  return { steps: mode === 'new' ? [...accountSteps, ...setupSteps] : setupSteps, accountCreated };
}
