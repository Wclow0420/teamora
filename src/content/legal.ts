/**
 * Teamora's privacy notice and terms of use — the single source for the in-app
 * Privacy / Terms screens and for `docs/legal/*.md` (which get hosted at a
 * public URL for App Store Connect). Edit the text HERE, then run
 * `node scripts/generate-legal-docs.js` so the markdown never drifts.
 *
 * Written plainly for Malaysian employees and employers, aligned with the
 * Personal Data Protection Act 2010 (PDPA). It describes only what the app
 * really does today (checked against the code: selfie + location at clock-in
 * only, receipt photos, statutory/bank fields, salary, push tokens). If a
 * feature changes what we collect, this text must change with it.
 *
 * THIS IS A DRAFT. It has not been reviewed by a lawyer. While `LEGAL_DRAFT`
 * is true, both screens show a "Draft — pending legal review" note. Flip it to
 * false only after legal sign-off (and after the drafting notes below are
 * resolved).
 */

/** Shows the "Draft — pending legal review" note on the legal screens while true. */
export const LEGAL_DRAFT = true;

export const LEGAL_DRAFT_NOTE = 'Draft — pending legal review';

/** Shown under each document title. Update whenever the text changes. */
export const LEGAL_LAST_UPDATED = '3 October 2026';

/** TODO(legal): placeholder mailbox — create it (or replace it) before publishing. */
export const PRIVACY_CONTACT_EMAIL = 'privacy@teamora.app';

/** A bullet is plain text, or a short bold lead-in (`term`) followed by text. */
export type LegalBullet = string | { term: string; text: string };

export type LegalSection = {
  heading: string;
  /** Paragraphs before the bullets. */
  body?: string[];
  bullets?: LegalBullet[];
  /** Paragraphs after the bullets. */
  after?: string[];
};

export type LegalDocument = {
  title: string;
  /** Opening paragraphs, shown before the first section. */
  intro: string[];
  sections: LegalSection[];
};

export const PRIVACY_NOTICE: LegalDocument = {
  title: 'Privacy notice',
  intro: [
    'This notice explains what personal data Teamora handles when your employer uses it for attendance, leave, claims and payroll — why it is used, who can see it, how long it is kept, and the choices you have.',
    "It is written for employees and employers in Malaysia and follows the Personal Data Protection Act 2010 (PDPA).",
  ],
  sections: [
    {
      heading: 'Who is responsible for your data',
      body: [
        'Your employer — the company that set up Teamora and added you to it — decides what personal data is collected about you and why. Under the PDPA, your employer is the data controller.',
        "Teamora provides the app and stores the data on your employer's behalf. We act as your employer's data processor: we use your data only to run the service for them, never for our own purposes.",
        "If you have a question about your data, start with your employer's HR team. You can also contact us (see \"Contact us\" below).",
      ],
    },
    {
      heading: 'What we collect',
      bullets: [
        {
          term: 'Account and work details',
          text: 'your name, work email, phone number, staff ID, job title, department, role, start date, reporting manager and assigned work site. Your password is stored only as a one-way hash, so nobody — including us — can read it.',
        },
        {
          term: 'Identity and statutory numbers',
          text: 'your NRIC number, EPF number, SOCSO number and income tax number, entered by your HR admin so your pay and statutory contributions can be worked out and reported.',
        },
        {
          term: 'Pay and bank details',
          text: 'your salary and pay basis, working days and hours, bank name and account number, and the payslips each payroll run produces (earnings, overtime, reimbursed claims, and deductions such as EPF, SOCSO, EIS and estimated PCB).',
        },
        {
          term: 'Tax relief details',
          text: 'your marital status, whether your spouse works and how many children you have — used only to estimate your monthly tax deduction (PCB).',
        },
        {
          term: 'Attendance',
          text: 'your clock-in and clock-out times, breaks, and whether you clocked in late.',
        },
        {
          term: 'Clock-in selfie',
          text: 'a photo taken with your front camera when you clock in (if you allow camera access), as proof that you clocked in yourself. It is a photo for a person to look at. Teamora does not run facial recognition on it or create a face template from it.',
        },
        {
          term: 'Location, at clock-in only',
          text: "if your employer has assigned you a work site, the app reads your location once — at the moment you clock in — to check you are within that site's area, and saves that position with the day's attendance record. The app does not track your location at any other time or in the background. If no work site is assigned to you, no location is collected.",
        },
        {
          term: 'Leave, claims and overtime',
          text: "the requests you submit (dates, amounts, notes), the receipt photos you attach to claims, and the approver's decision and any reason they give.",
        },
        {
          term: 'Shifts and calendar',
          text: 'the shifts your employer assigns you and the company calendar, such as public holidays.',
        },
        {
          term: 'Notifications and your device',
          text: 'the in-app notifications sent to you, and a push notification token for your phone so we can send you alerts. Your sign-in session is kept in your phone\'s secure storage.',
        },
      ],
      after: [
        'Your phone asks for your permission before Teamora can use the camera, your location or notifications. The camera is used only when you clock in or photograph a receipt, and location only when you clock in.',
        'The app has no advertising and no third-party analytics trackers.',
      ],
    },
    {
      heading: 'Why it is used',
      bullets: [
        'Recording your attendance and working hours — including checking you are at your work site when your employer requires it.',
        'Handling leave, claims and overtime requests and their approvals.',
        'Running payroll: calculating pay, statutory contributions (EPF, SOCSO and EIS) and estimated PCB, and producing your payslips.',
        'Producing the files your employer uses to pay salaries and make its statutory submissions.',
        'Sending you notifications about your requests, approvals and pay.',
        'Keeping accounts secure, preventing misuse and fixing problems.',
        'Meeting legal obligations that apply to your employer or to us.',
      ],
      after: ['We do not use your data for advertising, and we never sell it.'],
    },
    {
      heading: 'Do you have to provide it?',
      body: [
        'Details needed for your employment and pay — such as your NRIC, EPF, SOCSO and tax numbers, salary and bank account — are needed for your employer to pay you and meet its legal duties. If they are missing, your employer may not be able to process your pay or contributions through Teamora.',
        "Other items are up to you, such as a note on a leave request. You can refuse camera, location or notification permission on your phone. Without camera access you can still clock in, just without a selfie. If you have an assigned work site, though, your location is needed for a clock-in there to be accepted.",
      ],
    },
    {
      heading: 'Who can see it',
      bullets: [
        { term: 'You', text: 'your own details, attendance, requests and payslips.' },
        {
          term: 'Your reporting manager',
          text: 'the leave, claims and overtime requests you send them to approve, with the details needed to decide them.',
        },
        {
          term: "Your employer's HR admins and owner",
          text: 'your full employee record, including attendance, clock-in selfies, claims and payroll.',
        },
        {
          term: 'Service providers',
          text: 'companies that help us run Teamora, only as far as needed: our cloud hosting provider, Expo (push notifications and app updates), and Apple or Google (delivering notifications to your phone). They may use the data only to provide their service.',
        },
        {
          term: 'Government agencies',
          text: 'Teamora does not send your data to EPF (KWSP), SOCSO (PERKESO) or LHDN. Your employer downloads export files from Teamora and makes those submissions itself.',
        },
        {
          term: 'When the law requires it',
          text: 'we may disclose data where Malaysian law, a court order or a regulator requires it.',
        },
      ],
      after: [
        "Each company's data is kept separate: people in another company using Teamora cannot see yours.",
        'Our servers and some service providers (such as push notification services) may be located outside Malaysia. Where data is transferred abroad, it is done only as the PDPA allows.',
      ],
    },
    {
      heading: 'How long it is kept',
      body: [
        'Your employer decides how long your records are kept. Malaysian law requires employers to keep many payroll and employment records for several years — tax records, for example, are generally kept for 7 years — so your payslips and statutory records may be kept after you leave, even after your account is removed.',
        "When your employer removes your account, you can no longer sign in, your sign-in sessions and push notification tokens are deleted, and your work history stays in your employer's records.",
        "If an employer deletes its company from Teamora, all of that company's data — staff accounts, attendance (including selfies), leave, claims (including receipts), payroll and payslips — is permanently erased from Teamora's database straight away.",
      ],
    },
    {
      heading: 'How it is protected',
      bullets: [
        'The app talks to our servers over encrypted (HTTPS) connections.',
        "Passwords are stored only as one-way hashes, and your sign-in session is kept in your phone's secure storage.",
        'Access depends on role: managers see only their own team\'s requests, and only HR admins and the owner can see full employee records.',
        "Each company's data is kept separate from every other company's.",
      ],
      after: [
        "No system is perfectly secure. If a security incident affects your personal data, we will tell your employer without undue delay so the notifications the law requires can be made.",
      ],
    },
    {
      heading: 'Your rights',
      bullets: [
        {
          term: 'See your data',
          text: 'much of it is in the app (Profile → My details, Payslips, Leave and Claims). You can also ask your employer for a copy of the personal data they hold about you.',
        },
        {
          term: 'Correct your data',
          text: "you can update your phone number yourself in My details. For anything else that's wrong, ask your HR admin to correct it.",
        },
        {
          term: 'Limit what is collected',
          text: 'you can switch off camera, location or notification permission in your phone settings at any time (this may affect clock-in). You can also ask your employer to stop or limit using your data, though they may still need data the law or your employment requires.',
        },
        {
          term: 'Remove your account',
          text: 'Profile → Delete my account sends a request to your HR admin, who removes your account. Records your employer is legally required to keep stay with your employer.',
        },
        {
          term: 'Complain',
          text: 'if you are unhappy with how your data is handled, talk to your employer first. You can also complain to the Personal Data Protection Commissioner (Jabatan Perlindungan Data Peribadi).',
        },
      ],
    },
    {
      heading: 'Changes to this notice',
      body: [
        'We may update this notice when the app or the law changes. The date at the top shows when it last changed, and we will let you know in the app about important changes.',
      ],
    },
    {
      heading: 'Contact us',
      body: [
        "For questions about your own records, contact your employer's HR team — they can see and correct your data.",
        `For questions about how Teamora handles personal data, email ${PRIVACY_CONTACT_EMAIL}.`,
      ],
    },
  ],
};

export const TERMS_OF_USE: LegalDocument = {
  title: 'Terms of use',
  intro: [
    'These terms apply when you use the Teamora app. If your employer gave you access, your employer\'s own workplace policies also apply to how you use it at work.',
    'Please read them together with our Privacy notice, which explains how personal data is handled.',
  ],
  sections: [
    {
      heading: 'Your account',
      bullets: [
        'Keep your password private and do not share your account. You are responsible for what is done with it.',
        'Clock in and out only for yourself, and never ask someone else to do it for you.',
        "If you think someone else has used your account, change your password and tell your HR admin straight away.",
      ],
    },
    {
      heading: 'Use Teamora honestly',
      bullets: [
        'Do not submit false attendance, leave, claims, overtime or receipts.',
        "Do not try to see data you are not allowed to see — including other people's records or another company's data.",
        'Do not try to break, overload, copy or reverse-engineer the app or its servers.',
        'Do not upload anything unlawful, offensive or that you have no right to share.',
      ],
    },
    {
      heading: 'Pay figures',
      body: [
        'Teamora calculates pay, EPF, SOCSO and EIS, and an estimate of PCB (monthly tax deduction), from the information entered. The PCB figure is an estimate: it does not include year-to-date amounts and is not a filed or official figure.',
        'Your employer is responsible for checking payroll before it is approved and paid, and for its statutory submissions. Nothing in Teamora is tax, legal or financial advice.',
      ],
    },
    {
      heading: 'For employers (owners and HR admins)',
      bullets: [
        "Your company is the data controller for your staff's personal data. You are responsible for having a lawful reason to collect it and for telling your staff how it is used.",
        'You are responsible for the accuracy of the employee and pay details you enter, and for reviewing payroll before you approve and pay it.',
        'Malaysian law requires you to keep payroll, EPF, SOCSO and tax records. Export and keep your own copies of the records you must retain.',
        "Deleting your company erases all of its data permanently and straight away. It cannot be undone, so export what you need first.",
      ],
    },
    {
      heading: 'Availability',
      body: [
        "We work to keep Teamora available and accurate, but we can't promise it will always be uninterrupted or free of errors. We may change or improve features over time.",
      ],
    },
    {
      heading: 'Ending your use',
      bullets: [
        'Employees can ask for their account to be removed from Profile → Delete my account. Your HR admin can also deactivate your account, for example when you leave.',
        "A company's owner can delete the company and all of its data from Company settings.",
        'We may suspend an account that breaks these terms or puts other users or the service at risk.',
      ],
    },
    {
      heading: 'Liability',
      body: [
        'To the extent the law allows, Teamora is not liable for indirect or consequential loss arising from use of the app. Nothing in these terms takes away rights you have under Malaysian law that cannot be excluded.',
      ],
    },
    {
      heading: 'Changes and governing law',
      body: [
        'We may update these terms. The date at the top shows when they last changed, and we will tell you in the app about important changes. These terms are governed by the laws of Malaysia.',
      ],
    },
    {
      heading: 'Contact us',
      body: [
        `Questions about these terms? Ask your employer's HR team, or email ${PRIVACY_CONTACT_EMAIL}.`,
      ],
    },
  ],
};

/**
 * Open points for the lawyer — NOT shown in the app. They are copied into the
 * markdown exports under "Drafting notes" so reviewers see them in one place.
 */
export const LEGAL_DRAFTING_NOTES: string[] = [
  'Name the legal entity behind "Teamora" (company name, SSM registration number, registered address) — the text currently uses the product name.',
  `Create (or replace) the placeholder contact mailbox ${PRIVACY_CONTACT_EMAIL}.`,
  'PDPA notices must be given in both Bahasa Melayu and English — a BM version is needed before publishing.',
  'Confirm the hosting provider and region, and the basis for any cross-border transfer (PDPA as amended in 2024).',
  'Confirm database backup retention: deleted company data may persist in backups until they expire. The notice currently says data is erased from the live database straight away.',
  'Check the security-incident wording against the data breach notification and data processor duties added by the PDPA (Amendment) Act 2024, and whether a Data Protection Officer must be named.',
  'Confirm the record-keeping periods quoted (e.g. 7 years for tax records) and whether to cite the specific statutes (Income Tax Act 1967, EPF Act 1991, Employment Act 1955).',
  'Confirm production traffic is HTTPS-only (the app talks to whatever API URL the build is configured with).',
  'Decide whether a fee applies to data access requests and the response time to state.',
  'A data processing agreement between Teamora and each employer customer is needed to back the controller/processor split described here.',
];
