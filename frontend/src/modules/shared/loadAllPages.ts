import type { Page } from "./Pagination";

const DEFAULT_MAX_PAGES = 50;
const DEFAULT_MAX_ITEMS = 5_000;

/** Loads complete option catalogs without exceeding the server page-size limit. */
export async function loadAllPages<T>(
  load: (page: number, size: number) => Promise<Page<T>>,
  options: { maxPages?: number; maxItems?: number } = {},
): Promise<T[]> {
  const maxPages = options.maxPages ?? DEFAULT_MAX_PAGES;
  const maxItems = options.maxItems ?? DEFAULT_MAX_ITEMS;
  if (!Number.isInteger(maxPages) || maxPages < 1) throw new Error("Invalid pagination limit");
  if (!Number.isInteger(maxItems) || maxItems < 1) throw new Error("Invalid pagination limit");

  const items: T[] = [];
  for (let page = 0; ; page += 1) {
    if (page >= maxPages) throw new Error("Pagination response exceeds page limit");
    const result = await load(page, 100);
    if (!result || !Array.isArray(result.content) ||
        !Number.isInteger(result.totalPages) || result.totalPages < 0) {
      throw new Error("Invalid pagination response");
    }
    if (result.totalPages > maxPages) throw new Error("Pagination response exceeds page limit");
    items.push(...result.content);
    if (items.length > maxItems) throw new Error("Pagination response exceeds item limit");
    if (page + 1 >= result.totalPages) return items;
    if (result.content.length === 0) throw new Error("Incomplete pagination response");
  }
}
