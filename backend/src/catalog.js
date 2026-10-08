import {readFileSync} from "node:fs";
import {quoteSelection} from "./pricing.js";

export function loadCatalog(path = process.env.CATALOG_FILE) {
  if (!path) return [];
  const parsed = JSON.parse(readFileSync(path, "utf8"));
  if (!Array.isArray(parsed)) throw new TypeError("Catálogo deve ser uma lista de álbuns");
  // Validate all identifiers and reject duplicates before accepting requests.
  if (parsed.length) {
    const albumIds = new Set();
    const trackIds = new Set();
    for (const album of parsed) {
      if (!album || typeof album.id !== "string" || !album.id || albumIds.has(album.id) || !Array.isArray(album.tracks) || !album.tracks.length) {
        throw new TypeError("Álbum inválido ou duplicado");
      }
      albumIds.add(album.id);
      for (const track of album.tracks) {
        if (!track || typeof track.id !== "string" || !track.id || trackIds.has(track.id)) throw new TypeError("Faixa inválida ou duplicada");
        trackIds.add(track.id);
      }
    }
    // Exercise the same pricing rules as requests.
    quoteSelection(parsed, {albumIds:[parsed[0].id]});
  }
  return parsed.map(album=>({id:album.id,tracks:album.tracks.map(track=>({id:track.id}))}));
}
