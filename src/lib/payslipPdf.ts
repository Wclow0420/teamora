import * as Print from 'expo-print';
import * as Sharing from 'expo-sharing';
import type { EmployeeResponse, Payslip } from '@/api/types';
import { payslipHtml } from './payslipHtml';

/**
 * Generates a branded PDF payslip from the data already on the client and opens the
 * native share sheet (save to Files, email, etc.). Uses `expo-print` (HTML → PDF) +
 * `expo-sharing`; both work in Expo Go and dev/EAS builds.
 */
export async function sharePayslipPdf(p: Payslip, employee: EmployeeResponse | null): Promise<void> {
  const html = payslipHtml(p, employee, new Date());
  const { uri } = await Print.printToFileAsync({ html });
  if (await Sharing.isAvailableAsync()) {
    await Sharing.shareAsync(uri, {
      mimeType: 'application/pdf',
      dialogTitle: `Payslip · ${p.periodLabel}`,
      UTI: 'com.adobe.pdf',
    });
  }
}
