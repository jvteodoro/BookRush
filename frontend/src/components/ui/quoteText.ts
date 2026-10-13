// Display-only: do not change the excerpt text used for lineage or telemetry.
export function quoteText(text: string): string {
  const value = text.trim();
  const pairs: Record<string, string> = { '“': '”', '«': '»', '"': '"' };
  return value.length > 1 && pairs[value[0]] === value.at(-1) ? value.slice(1, -1).trim() : value;
}
