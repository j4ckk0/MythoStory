/**
 * The i18n key of a generation's label — "the Trojan War's generation", "one generation before", "N generations
 * before" —: the timeline's rows and a story's page say it alike.
 */
export function generationKey(generation: number): string {
  return generation === 0 ? 'timeline.generation.zero' : generation === 1 ? 'timeline.generation.one' : 'timeline.generation';
}
