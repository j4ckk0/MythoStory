/** The UI's English messages: the reference set of i18n keys, which fr.ts must match key for key. */
export const en = {
  'app.title': 'MythoStory',
  'app.tagline': 'The myths of ancient Greece: their characters, their stories, and where to read them.',
  'app.loading': 'Loading the myths…',
  'app.unreachable': 'The MythoStory server does not answer.',
  'app.version': 'Version {version} · {platform}',

  'nav.home': 'Home',
  'nav.characters': 'Characters',
  'nav.stories': 'Stories',
  'nav.search': 'Search',
  'nav.search.placeholder': 'Search a character or a story',

  'i18n.language': 'Language',
  'i18n.switch': 'Lire en français',
  'i18n.name.en': 'In English',
  'i18n.name.fr': 'In French',

  'kind.primordial': 'Primordial deities',
  'kind.titan': 'Titans',
  'kind.olympian': 'Olympians',
  'kind.deity': 'Other deities',
  'kind.nymph': 'Nymphs',
  'kind.hero': 'Heroes and demigods',
  'kind.mortal': 'Mortals',
  'kind.creature': 'Creatures and monsters',
  'kind.all': 'All',

  'home.characters': 'The characters',
  'home.cycles': 'The stories',
  'home.counts': '{characters} characters · {stories} stories',

  'characters.title': 'Characters',
  'characters.count': '{count} characters',
  'characters.allLetters': 'All',
  'characters.letters': 'By letter',

  'character.greek': 'In Greek',
  'character.roman': 'For the Romans',
  'character.parents': 'Parents',
  'character.children': 'Children',
  'character.stories': 'Stories',
  'character.noStories': 'No story tells of them yet.',
  'character.notFound': 'No character here.',
  'character.wikidata': 'Wikidata',

  'stories.title': 'Stories',

  'story.characters': 'Characters',
  'story.sources': 'Ancient sources',
  'story.read': 'Read it',
  'story.related': 'See also',
  'story.previous': 'Previous',
  'story.next': 'Next',
  'story.notFound': 'No story here.',
  'story.cycle': 'Part {position} of {count}',

  'links.title': 'To go further',
  'links.wikipedia': 'Wikipedia',
  'links.article': 'Articles',
  'links.video': 'Videos',

  'search.title': 'Search',
  'search.none': 'Nothing matches “{query}”.',
  'search.more': '…and {count} more: refine your search.',
  'search.hint': 'Type a name — Zeus, Heracles, Medusa — or the title of a story.',
} as const;

/** An i18n key: one of the UI's messages. */
export type I18nKey = keyof typeof en;
