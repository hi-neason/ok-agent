import type { Page } from "./Pagination";

/** Loads complete option catalogs without exceeding the server page-size limit. */
export async function loadAllPages<T>(
  load: (page: number, size: number) => Promise<Page<T>>,
): Promise<T[]> {
  const items: T[] = [];
  for (let page = 0; ; page += 1) {
    const result = await load(page, 100);
    if (!result || !Array.isArray(result.content) ||
        !Number.isInteger(result.totalPages) || result.totalPages < 0) {
      throw new Error("Invalid pagination response");
    }
    items.push(...result.content);
    if (page + 1 >= result.totalPages) return items;
    if (result.content.length === 0) throw new Error("Incomplete pagination response");
  }
}
