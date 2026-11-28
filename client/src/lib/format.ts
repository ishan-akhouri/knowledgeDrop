export function formatDateTime(iso: string): string {
  const d = new Date(iso);
  return Number.isNaN(d.getTime())
    ? iso
    : d.toLocaleString(undefined, { dateStyle: 'medium', timeStyle: 'short' });
}

/** Compact date for table cells, e.g. "Nov 25, 9:46 PM". */
export function formatShort(iso: string): string {
  const d = new Date(iso);
  return Number.isNaN(d.getTime())
    ? iso
    : d.toLocaleString(undefined, { month: 'short', day: 'numeric', hour: 'numeric', minute: '2-digit' });
}

export function maskToken(token: string): string {
  if (token.length <= 8) return '••••••••';
  return `${token.slice(0, 4)}${'•'.repeat(12)}${token.slice(-4)}`;
}
