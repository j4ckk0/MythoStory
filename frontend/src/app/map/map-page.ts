import { Component, computed, inject, input } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { CatalogService } from '../catalog/catalog';
import { I18nPipe, I18nTextPipe } from '../i18n/i18n.pipe';
import { MapView } from './map-view';

/**
 * The map page: every story's places and journeys; ?story= draws one in front, ?place= tells a place's stories —
 * both in the URL, so that a view can be linked to (the story pages do).
 */
@Component({
  selector: 'app-map-page',
  imports: [RouterLink, I18nPipe, I18nTextPipe, MapView],
  templateUrl: './map-page.html',
})
export class MapPage {
  private readonly catalog = inject(CatalogService);
  private readonly router = inject(Router);
  /** The ?story= query parameter. */
  readonly story = input<string | undefined>();
  /** The ?place= query parameter. */
  readonly place = input<string | undefined>();

  /** The stories with a place, in the cycles' order. */
  protected readonly stories = computed(() => this.catalog.storiesInCycleOrder().filter((s) => s.places?.length));
  protected readonly selectedPlace = computed(() => this.catalog.placeById().get(this.place() ?? ''));
  protected readonly placeStories = computed(() => this.catalog.storiesByPlace().get(this.place() ?? '') ?? []);
  protected readonly cycleOf = (id: string) => this.catalog.cycleOfStory().get(id)?.id ?? '';

  protected selectPlace(id: string): void {
    this.router.navigate([], { queryParams: { place: id }, queryParamsHandling: 'merge', replaceUrl: true });
  }
}
