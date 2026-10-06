/**
 * The map's projection: equirectangular, its longitudes shrunk by the cosine of the Aegean's latitude, so that Greece
 * keeps its shape; ten SVG units to a degree of latitude, north up.
 */
const UNITS_PER_DEGREE = 10;
const LONGITUDE_FACTOR = Math.cos((38 * Math.PI) / 180);

export function project(lon: number, lat: number): [number, number] {
  return [lon * LONGITUDE_FACTOR * UNITS_PER_DEGREE, -lat * UNITS_PER_DEGREE];
}

export interface ViewBox {
  x: number;
  y: number;
  width: number;
  height: number;
}

/** The whole map: from Gibraltar to the Caucasus, from Libya to the Danube. */
export const WHOLE_MAP = { west: -10, east: 47, south: 28, north: 47.5 };

/** The view showing the given points, with a margin (a share of their extent), at least minDegrees wide, in the map's aspect ratio. */
export function fit(points: [number, number][], aspect: number, minDegrees = 6, margin = 0.3): ViewBox {
  const lons = points.map(([lon]) => lon);
  const lats = points.map(([, lat]) => lat);
  const [west, east, south, north] = [Math.min(...lons), Math.max(...lons), Math.min(...lats), Math.max(...lats)];
  const [x1, y1] = project(west, north);
  const [x2, y2] = project(east, south);
  const min = minDegrees * UNITS_PER_DEGREE;
  let width = Math.max(x2 - x1, min) * (1 + margin);
  let height = Math.max(y2 - y1, min / aspect) * (1 + margin);
  if (width / height > aspect) height = width / aspect;
  else width = height * aspect;
  return { x: (x1 + x2) / 2 - width / 2, y: (y1 + y2) / 2 - height / 2, width, height };
}
