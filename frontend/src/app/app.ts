import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Info, Story, StoryService } from './story';

@Component({
  imports: [FormsModule],
  selector: 'app-root',
  styleUrl: './app.css',
  templateUrl: './app.html',
})
export class App {
  private readonly stories$ = inject(StoryService);

  protected readonly info = signal<Info | null>(null);
  protected readonly stories = signal<Story[]>([]);
  protected readonly error = signal<string | null>(null);
  protected title = '';
  protected summary = '';

  constructor() {
    this.stories$.info().subscribe({ next: (info) => this.info.set(info), error: () => this.unreachable() });
    this.stories$.list().subscribe({ next: (stories) => this.stories.set(stories), error: () => this.unreachable() });
  }

  protected add(): void {
    const title = this.title.trim();
    if (!title) return;
    this.stories$.create(title, this.summary.trim()).subscribe({
      next: (story) => {
        this.stories.update((stories) => [...stories, story]);
        this.title = '';
        this.summary = '';
      },
      error: () => this.unreachable(),
    });
  }

  private unreachable(): void {
    this.error.set('The MythoStory backend does not answer.');
  }
}
