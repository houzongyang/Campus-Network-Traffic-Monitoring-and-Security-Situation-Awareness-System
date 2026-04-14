/**
 * Projection Module
 * Encapsulates WGS84 (lon, lat) to planar coordinate transformation.
 * Supports Equirectangular (Plate Carrée) projection as requested.
 */

/**
 * Projects WGS84 coordinates to planar coordinates.
 * Type: Equirectangular Projection (等距圆柱投影)
 * Parameters: Standard Parallel = 0°
 * 
 * @param {number} lon - Longitude in degrees [-180, 180]
 * @param {number} lat - Latitude in degrees [-90, 90]
 * @returns {[number, number]} Planar coordinates [x, y] normalized to [-1, 1]
 */
export const project = (lon, lat) => {
  // Map longitude [-180, 180] to [-1, 1]
  const x = lon / 180;
  // Map latitude [-90, 90] to [1, -1] (Y-axis points down in most 2D graphics)
  const y = -lat / 90;
  return [x, y];
};

/**
 * Inverts planar coordinates back to WGS84 coordinates.
 * 
 * @param {number} x - Planar X coordinate [-1, 1]
 * @param {number} y - Planar Y coordinate [-1, 1]
 * @returns {[number, number]} WGS84 coordinates [lon, lat]
 */
export const invert = (x, y) => {
  const lon = x * 180;
  const lat = -y * 90;
  return [lon, lat];
};
