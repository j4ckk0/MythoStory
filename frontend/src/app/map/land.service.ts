import { HttpClient } from '@angular/common/http';
import { Injectable, inject, signal } from '@angular/core';
import { project } from './projection';

interface Geometry {
  type: 'Polygon' | 'MultiPolygon';
  coordinates: number[][][] | number[][][][];
}

/**
 * The coastlines, as one SVG path: map/land.json — Natural Earth's 1:10m land (public domain), clipped to the
 * Mediterranean and the Black Sea and simplified (docs/DEVELOPING.md: The map). A static file: the Android app has it offline.
 */
@Injectable({ providedIn: 'root' })
export class LandService {
  readonly path = signal<string | null>(null);

  constructor() {
    inject(HttpClient)
      .get<{ features: { geometry: Geometry | null }[] }>('map/land.json')
      .subscribe((land) => this.path.set(land.features.map((f) => toPath(f.geometry)).join('')));
  }
}

function toPath(geometry: Geometry | null): string {
  if (!geometry) return '';
  const polygons = (geometry.type === 'Polygon' ? [geometry.coordinates] : geometry.coordinates) as number[][][][];
  return polygons
    .flatMap((rings) => rings)
    .map((ring) => 'M' + ring.map(([lon, lat]) => project(lon, lat).map((v) => v.toFixed(1)).join(',')).join('L') + 'Z')
    .join('');
}
