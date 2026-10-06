/** For matching and sorting names whatever their accents and case: "Héraclès" → "heracles". */
export function normalize(text: string): string {
  return text
    .normalize('NFD')
    .replace(/[̀-ͯ]/g, '')
    .replace(/œ/g, 'oe')
    .toLowerCase();
}

/** The paragraphs of a summary: separated by a blank line in the data files. */
export function paragraphs(text: string): string[] {
  return text.split(/\n\s*\n/).map((p) => p.trim()).filter(Boolean);
}
