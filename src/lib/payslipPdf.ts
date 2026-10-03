import * as Print from 'expo-print';
import * as Sharing from 'expo-sharing';
import { File, Paths } from 'expo-file-system';
import type { EmployeeResponse, Payslip } from '@/api/types';
import { payslipHtml } from './payslipHtml';

/** "Payslip-Amir-Hakim-2026-09.pdf" — readable when saved to Files or emailed. */
function payslipFilename(p: Payslip, employee: EmployeeResponse | null): string {
  const name = (employee?.fullName ?? '')
    .normalize('NFKD')
    .replace(/[^A-Za-z0-9]+/g, '-')
    .replace(/^-+|-+$/g, '');
  return `Payslip${name ? `-${name}` : ''}-${p.period}.pdf`;
}

/**
 * Generates a branded PDF payslip from the data already on the client and opens the
 * native share sheet (save to Files, email, etc.). Uses `expo-print` (HTML → PDF) +
 * `expo-sharing`; both work in Expo Go and dev/EAS builds.
 */
export async function sharePayslipPdf(p: Payslip, employee: EmployeeResponse | null): Promise<void> {
  const html = payslipHtml(p, employee, new Date());
  const { uri } = await Print.printToFileAsync({ html });

  // expo-print names the file with a random id; copy it to a readable name.
  let shareUri = uri;
  try {
    const named = new File(Paths.cache, payslipFilename(p, employee));
    if (named.exists) named.delete();
    new File(uri).copy(named);
    shareUri = named.uri;
  } catch {
    // Fall back to the generated file — a random name is better than no payslip.
  }

  if (await Sharing.isAvailableAsync()) {
    await Sharing.shareAsync(shareUri, {
      mimeType: 'application/pdf',
      dialogTitle: `Payslip · ${p.periodLabel}`,
      UTI: 'com.adobe.pdf',
    });
  }
}
