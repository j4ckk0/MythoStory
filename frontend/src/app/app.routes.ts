import { Routes } from '@angular/router';
import { CharacterPage } from './pages/character';
import { Characters } from './pages/characters';
import { Home } from './pages/home';
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
  { path: 'search', component: Search },
  { path: '**', redirectTo: '' },
];
