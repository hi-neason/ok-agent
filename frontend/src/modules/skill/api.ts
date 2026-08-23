import type { SkillFileContent, SkillFileItem, SkillItem } from "./types";
import type { Page } from "../shared";

export async function fetchSkills(
  page = 0,
  size = 20,
): Promise<Page<SkillItem>> {
  const response = await fetch(`/api/v1/skills?page=${page}&size=${size}`);
  if (!response.ok) throw new Error("load failed");
  return (await response.json()) as Page<SkillItem>;
}

export async function saveSkillMetadata(
  id: string,
  payload: { name: string; description: string; businessDomain: string },
): Promise<SkillItem> {
  const response = await fetch(`/api/v1/skills/${id}/metadata`, {
    method: "PUT",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(payload),
  });
  if (!response.ok) throw new Error("save failed");
  return (await response.json()) as SkillItem;
}

export class SkillConflictError extends Error {
  constructor() {
    super("skill conflict");
    this.name = "SkillConflictError";
  }
}

export async function importSkillArchive(form: FormData): Promise<SkillItem> {
  const response = await fetch("/api/v1/skills/import", {
    method: "POST",
    body: form,
  });
  if (response.status === 409) throw new SkillConflictError();
  if (!response.ok) {
    const failure = (await response.json().catch(() => null)) as {
      code?: string;
    } | null;
    if (failure?.code === "SKILL_MD_NOT_AT_ROOT")
      throw new Error("SKILL_MD_NOT_AT_ROOT");
    throw new Error("import failed");
  }
  return (await response.json()) as SkillItem;
}

export async function fetchSkillFiles(id: string): Promise<SkillFileItem[]> {
  const response = await fetch(`/api/v1/skills/${id}/files`);
  if (!response.ok) throw new Error("files failed");
  return (await response.json()) as SkillFileItem[];
}

export async function fetchSkillFile(
  id: string,
  path: string,
): Promise<SkillFileContent> {
  const response = await fetch(
    `/api/v1/skills/${id}/file?path=${encodeURIComponent(path)}`,
  );
  if (!response.ok) throw new Error("file failed");
  return (await response.json()) as SkillFileContent;
}

export class SkillFileConflictError extends Error {
  constructor() {
    super("file conflict");
    this.name = "SkillFileConflictError";
  }
}

export async function saveSkillFile(
  id: string,
  payload: { path: string; content: string; version: number },
): Promise<SkillFileContent> {
  const response = await fetch(`/api/v1/skills/${id}/file`, {
    method: "PUT",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(payload),
  });
  if (response.status === 409) throw new SkillFileConflictError();
  if (!response.ok) throw new Error("file save failed");
  return (await response.json()) as SkillFileContent;
}

export async function setSkillEnabled(
  id: string,
  enabled: boolean,
): Promise<SkillItem> {
  const response = await fetch(
    `/api/v1/skills/${id}/enabled?value=${enabled}`,
    { method: "PATCH" },
  );
  if (!response.ok) throw new Error("status failed");
  return (await response.json()) as SkillItem;
}

export async function deleteSkill(id: string): Promise<void> {
  const response = await fetch(`/api/v1/skills/${id}`, { method: "DELETE" });
  if (!response.ok) throw new Error("delete failed");
}
