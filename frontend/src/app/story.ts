import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

/** A story, as the backend's /api/stories serves it (core: Story.kt). */
export interface Story {
  id: string;
  title: string;
  summary: string;
}

/** What the backend says of itself (backend: InfoRoutes.kt): "desktop" or "android" as platform. */
export interface Info {
  name: string;
  version: string;
  platform: string;
}

/** The backend's API, the same on the desktop and on Android: the page is always served by it, so relative URLs do. */
@Injectable({ providedIn: 'root' })
export class StoryService {
  private readonly http = inject(HttpClient);

  info(): Observable<Info> {
    return this.http.get<Info>('api/info');
  }

  list(): Observable<Story[]> {
    return this.http.get<Story[]>('api/stories');
  }

  create(title: string, summary: string): Observable<Story> {
    return this.http.post<Story>('api/stories', { title, summary });
  }
}
