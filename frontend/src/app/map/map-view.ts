import { Component, ElementRef, computed, effect, inject, input, output, signal, viewChild } from '@angular/core';
import { CatalogService, Place, Story } from '../catalog/catalog';
import { I18nPipe } from '../i18n/i18n.pipe';
import { I18nService } from '../i18n/i18n.service';
import { LandService } from './land.service';
import { ViewBox, WHOLE_MAP, fit, project } from './projection';

/** One place as drawn: where, and its label when it has room for one. */
interface DrawnPlace {
  place: Place;
  x: number;
  y: number;
  name: string;
  uncertain: boolean;
  highlighted: boolean;
  label: { x: number; y: number; anchor: 'start' | 'end' } | null;
}

interface Box {
  x1: number;
  y1: number;
  x2: number;
  y2: number;
}

/**
 * The map of the myths: the coastlines, the places of the given stories and their journeys. Interactive (zoom with
 * the wheel or the buttons, pan by dragging) unless it is a story's inset. A place clicked is told to the parent.
 */
@Component({
  selector: 'app-map-view',
  imports: [I18nPipe],
  templateUrl: './map-view.html',
  styleUrl: './map-view.css',
})
export class MapView {
  private readonly catalog = inject(CatalogService);
  private readonly i18n = inject(I18nService);
  protected readonly land = inject(LandService);
  private readonly svg = viewChild<ElementRef<SVGSVGElement>>('svg');

  /** The stories whose places and journeys are drawn. */
  readonly stories = input<Story[]>([]);
  /** The story drawn in front, its places labelled; the others fade. */
  readonly highlight = input<string | null>(null);
  readonly selectedPlace = input<string | null>(null);
  /** False for an inset: no zoom, no panning, the view fitted to the stories. */
  readonly interactive = input(true);
  readonly placeSelected = output<string>();

  protected readonly aspect = computed(() => (this.interactive() ? 16 / 10 : 16 / 9));

  /**
   * The view that shows what there is to see: an inset's places, the highlighted story's on the map page, else the
   * whole map.
   */
  private readonly home = computed<ViewBox>(() => {
    const highlighted = this.catalog.storyById().get(this.highlight() ?? '')?.places ?? [];
    const shown = this.interactive() ? highlighted : this.places().map(({ place }) => place.id);
    const points = shown
      .map((id) => this.catalog.placeById().get(id))
      .filter((p): p is Place => !!p)
      .map((p) => [p.lon, p.lat] as [number, number]);
    if (points.length) return fit(points, this.aspect(), 6, 0.45);
    return fit([[WHOLE_MAP.west, WHOLE_MAP.north], [WHOLE_MAP.east, WHOLE_MAP.south]], this.aspect(), 0, 0);
  });
  protected readonly view = signal<ViewBox>({ x: 0, y: 0, width: 1, height: 1 });
  protected readonly viewBox = computed(() => {
    const v = this.view();
    return `${v.x} ${v.y} ${v.width} ${v.height}`;
  });
  /** The size of a dot, a label, a line, in map units: a constant share of the view, so constant on the screen. */
  protected readonly unit = computed(() => this.view().width / (this.interactive() ? 220 : 160));

  /** Every place of the stories, once. */
  private readonly places = computed(() => {
    const ids = new Set(this.stories().flatMap((s) => s.places ?? []));
    return [...ids].map((id) => this.catalog.placeById().get(id)).filter((p): p is Place => !!p).map((place) => ({ place }));
  });

  /**
   * The places, and the labels of those that deserve one — the highlighted story's, the selected one, all of them
   * when zoomed in —, laid out greedily: the selected place first, each label trying the four sides of its dot and
   * given up when every side would overlap a label already placed or leave the view (the dot's tooltip still names it).
   */
  protected readonly drawnPlaces = computed<DrawnPlace[]>(() => {
    this.i18n.language();
    const highlighted = new Set(this.catalog.storyById().get(this.highlight() ?? '')?.places ?? []);
    const view = this.view();
    const unit = this.unit();
    const zoomedIn = view.width < 150;
    const drawn = this.places().map(({ place }) => {
      const [x, y] = project(place.lon, place.lat);
      return {
        place,
        x,
        y,
        name: this.i18n.text(place.name),
        uncertain: !!place.identification,
        highlighted: highlighted.has(place.id) || place.id === this.selectedPlace() || !this.interactive() || zoomedIn,
        label: null as DrawnPlace['label'],
      };
    });
    const font = unit * 4.2;
    const taken: Box[] = drawn.map((d) => ({ x1: d.x - unit * 2, y1: d.y - unit * 2, x2: d.x + unit * 2, y2: d.y + unit * 2 }));
    const order = [...drawn].sort((a, b) => Number(b.place.id === this.selectedPlace()) - Number(a.place.id === this.selectedPlace()));
    for (const d of order.filter((p) => p.highlighted)) {
      const width = d.name.length * font * 0.55;
      const gap = unit * 2.5;
      const candidates: NonNullable<DrawnPlace['label']>[] = [
        { x: d.x + gap, y: d.y + font * 0.35, anchor: 'start' },
        { x: d.x - gap, y: d.y + font * 0.35, anchor: 'end' },
        { x: d.x + gap * 0.5, y: d.y - gap, anchor: 'start' },
        { x: d.x + gap * 0.5, y: d.y + gap + font * 0.7, anchor: 'start' },
      ];
      for (const c of candidates) {
        const x1 = c.anchor === 'start' ? c.x : c.x - width;
        const box = { x1, y1: c.y - font * 0.8, x2: x1 + width, y2: c.y + font * 0.25 };
        const inView = box.x1 >= view.x && box.x2 <= view.x + view.width && box.y1 >= view.y && box.y2 <= view.y + view.height;
        const free = taken.every((t) => box.x2 < t.x1 || box.x1 > t.x2 || box.y2 < t.y1 || box.y1 > t.y2 || isOwnDot(t, d, unit));
        if (inView && free) {
          d.label = c;
          taken.push(box);
          break;
        }
      }
    }
    return drawn;
  });

  /** The journeys: each story whose places are a route, as a polyline in its cycle's colour. */
  protected readonly routes = computed(() =>
    this.stories()
      .filter((s) => s.route)
      .map((story) => ({
        story,
        cycle: this.catalog.cycleOfStory().get(story.id)?.id ?? '',
        points: (story.places ?? [])
          .map((id) => this.catalog.placeById().get(id))
          .filter((p): p is Place => !!p)
          .map((p) => project(p.lon, p.lat).join(','))
          .join(' '),
        highlighted: story.id === this.highlight(),
      })),
  );

  protected readonly faded = computed(() => this.highlight() !== null);

  constructor() {
    // back to the home view whenever what it shows changes
    effect(() => this.view.set(this.home()));
  }

  protected select(place: Place): void {
    this.placeSelected.emit(place.id);
  }

  protected zoom(factor: number, centre?: [number, number]): void {
    const v = this.view();
    const [cx, cy] = centre ?? [v.x + v.width / 2, v.y + v.height / 2];
    const home = this.home();
    const width = Math.min(Math.max(v.width * factor, home.width / 40), home.width);
    const height = width / this.aspect();
    const k = width / v.width;
    this.view.set({ x: cx - (cx - v.x) * k, y: cy - (cy - v.y) * k, width, height });
  }

  protected reset(): void {
    this.view.set(this.home());
  }

  protected onWheel(event: WheelEvent): void {
    if (!this.interactive()) return;
    event.preventDefault();
    this.zoom(event.deltaY > 0 ? 1.25 : 0.8, this.toMap(event));
  }

  private drag: { x: number; y: number; view: ViewBox } | null = null;

  protected onPointerDown(event: PointerEvent): void {
    if (!this.interactive() || event.button !== 0) return;
    this.drag = { x: event.clientX, y: event.clientY, view: this.view() };
  }

  protected onPointerMove(event: PointerEvent): void {
    const svg = this.svg()?.nativeElement;
    if (!this.drag || !svg) return;
    const scale = this.drag.view.width / svg.clientWidth;
    const dx = (event.clientX - this.drag.x) * scale;
    const dy = (event.clientY - this.drag.y) * scale;
    if (Math.abs(dx) + Math.abs(dy) > 0.5 * scale) svg.setPointerCapture(event.pointerId);
    this.view.set({ ...this.drag.view, x: this.drag.view.x - dx, y: this.drag.view.y - dy });
  }

  protected onPointerUp(): void {
    this.drag = null;
  }

  /** A pointer's position in map units. */
  private toMap(event: MouseEvent): [number, number] {
    const svg = this.svg()?.nativeElement;
    const matrix = svg?.getScreenCTM()?.inverse();
    if (!svg || !matrix) return [this.view().x + this.view().width / 2, this.view().y + this.view().height / 2];
    const point = new DOMPoint(event.clientX, event.clientY).matrixTransform(matrix);
    return [point.x, point.y];
  }
}

/** Whether the box is the place's own dot, which its label may touch. */
function isOwnDot(box: Box, place: { x: number; y: number }, unit: number): boolean {
  return box.x1 === place.x - unit * 2 && box.y1 === place.y - unit * 2;
}
