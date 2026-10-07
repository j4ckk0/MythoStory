import { Routes } from '@angular/router';
import { CharacterPage } from './pages/character';
import { Characters } from './pages/characters';
import { Help } from './help/help';
import { MapPage } from './map/map-page';
import { Home } from './pages/home';
import { Timeline } from './timeline/timeline';
import { Search } from './pages/search';
import { Stories } from './pages/stories';
import { StoryPage } from './pages/story';

/** The page's places; their parameters reach the components as inputs (withComponentInputBinding). */
export const routes: Routes = [
  { path: '', component: Home },
  { path: 'characters', component: Characters },
  { path: 'characters/:id', component: CharacterPage },
  { path: 'stories', component: Stories },
  { path: 'stories/:id', component: StoryPage },
  { path: 'timeline', component: Timeline },
  { path: 'map', component: MapPage },
  { path: 'search', component: Search },
  { path: 'help', component: Help },
  { path: '**', redirectTo: '' },
];
