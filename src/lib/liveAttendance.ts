import type { LiveStaffRow } from '@/api/types';

/**
 * Live-attendance grouping (framework-agnostic). Mirrors how the backend counts
 * the board's stat tiles, so a filter always shows exactly the people its tile
 * counted: LATE staff are in the office too, ON_LEAVE + ABSENT are "out".
 */
export type LiveFilter = 'all' | 'in' | 'remote' | 'late' | 'out';

export function matchesLiveFilter(row: LiveStaffRow, filter: LiveFilter): boolean {
  const status = row.status.toUpperCase();
  switch (filter) {
    case 'in':
      return status === 'PRESENT' || status === 'WORKING' || status === 'LATE';
    case 'remote':
      return status === 'REMOTE';
    case 'late':
      return status === 'LATE';
    case 'out':
      return status === 'ON_LEAVE' || status === 'ABSENT';
    default:
      return true;
  }
}

/** Plain-language chip label for a row's status (the API sends the raw enum). */
export function liveStatusLabel(status: string): string {
  switch (status.toUpperCase()) {
    case 'PRESENT':
    case 'WORKING':
      return 'In';
    case 'LATE':
      return 'Late';
    case 'REMOTE':
      return 'Remote';
    case 'ON_LEAVE':
      return 'On leave';
    case 'ABSENT':
      return 'Out';
    default:
      return status;
  }
}
