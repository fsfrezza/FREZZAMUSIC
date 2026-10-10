export const TRACK_CENTS = 199;
export const ALBUM_CENTS = 1499;

export function quoteSelection(catalog, selection) {
  if (!Array.isArray(catalog) || !selection || typeof selection !== "object") {
    throw new TypeError("Catálogo ou seleção inválida");
  }
  const requestedAlbums = new Set(selection.albumIds ?? []);
  const requestedTracks = new Set(selection.trackIds ?? []);
  if (![...requestedAlbums, ...requestedTracks].every(id => typeof id === "string" && id.length > 0)) {
    throw new TypeError("IDs inválidos");
  }
  const knownAlbums = new Set();
  const knownTracks = new Set();
  const items = [];
  for (const album of catalog) {
    if (!album || typeof album.id !== "string" || !Array.isArray(album.tracks) || album.tracks.length === 0 || knownAlbums.has(album.id)) {
      throw new TypeError("Álbum inválido ou duplicado");
    }
    knownAlbums.add(album.id);
    const ids = album.tracks.map(t => t.id);
    if (ids.some(id => typeof id !== "string" || !id || knownTracks.has(id)) || new Set(ids).size !== ids.length) {
      throw new TypeError("Faixas inválidas ou duplicadas");
    }
    ids.forEach(id => knownTracks.add(id));
    const selected = requestedAlbums.has(album.id) ? ids : ids.filter(id => requestedTracks.has(id));
    if (selected.length === ids.length) {
      items.push({type:"album", id:album.id, trackIds:[...ids], amountCents:ALBUM_CENTS});
    } else {
      for (const id of selected) items.push({type:"track", id, trackIds:[id], amountCents:TRACK_CENTS});
    }
  }
  if ([...requestedAlbums].some(id => !knownAlbums.has(id)) || [...requestedTracks].some(id => !knownTracks.has(id))) {
    throw new RangeError("Item desconhecido");
  }
  if (items.length === 0) throw new RangeError("Seleção vazia");
  return {currency:"BRL", items, totalCents:items.reduce((sum,item)=>sum+item.amountCents,0)};
}
