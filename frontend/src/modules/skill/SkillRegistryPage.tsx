import { useEffect, useState } from "react";
import { createPortal } from "react-dom";
import { useTranslation } from "react-i18next";
import { Button, PageHeader, Toggle, useConfirm } from "../shared";
import {
  SkillConflictError,
  SkillFileConflictError,
  deleteSkill,
  fetchSkillFile,
  fetchSkillFiles,
  fetchSkills,
  importSkillArchive,
  saveSkillFile,
  saveSkillMetadata,
  setSkillEnabled,
} from "./api";
import { buildSkillTree, type SkillFileContent, type SkillItem, type SkillTreeNode } from "./types";

function SkillTree({
  nodes,
  selectedPath,
  onSelect,
}: {
  nodes: SkillTreeNode[];
  selectedPath?: string;
  onSelect: (path: string) => void;
}) {
  const [collapsed, setCollapsed] = useState<Set<string>>(() => new Set());

  const toggleFolder = (path: string) => {
    setCollapsed((current) => {
      const next = new Set(current);
      if (next.has(path)) next.delete(path);
      else next.add(path);
      return next;
    });
  };

  const renderNodes = (items: SkillTreeNode[], depth: number) =>
    items.map((node) => {
      const folder = !node.file;
      const isCollapsed = collapsed.has(node.path);
      return (
        <div key={node.path}>
          <button
            className={`${selectedPath === node.path ? "selected" : ""} ${folder ? "skill-tree-folder" : ""}`}
            style={{ paddingLeft: 10 + depth * 17 }}
            onClick={() =>
              folder ? toggleFolder(node.path) : onSelect(node.path)
            }
            aria-expanded={folder ? !isCollapsed : undefined}
          >
            <span>{folder ? (isCollapsed ? "▸" : "▾") : "◇"}</span>
            {node.name}
          </button>
          {node.children.length > 0 &&
            !isCollapsed &&
            renderNodes(node.children, depth + 1)}
        </div>
      );
    });

  return <>{renderNodes(nodes, 0)}</>;
}

export function SkillRegistryPage() {
  const { t } = useTranslation();
  const { confirm, Dialog } = useConfirm();
  const [skills, setSkills] = useState<SkillItem[]>([]);
  const [uploadOpen, setUploadOpen] = useState(false);
  const [editing, setEditing] = useState<SkillItem | null>(null);
  const [viewing, setViewing] = useState<SkillItem | null>(null);
  const [files, setFiles] = useState<{ path: string; mediaType: string; size: number }[]>([]);
  const [selectedFile, setSelectedFile] = useState<SkillFileContent | null>(
    null,
  );
  const [fileDraft, setFileDraft] = useState<string | null>(null);
  const [fileSaving, setFileSaving] = useState(false);
  const [fileError, setFileError] = useState("");
  const [fileSuccess, setFileSuccess] = useState("");
  const [archive, setArchive] = useState<File | null>(null);
  const [uploadName, setUploadName] = useState("");
  const [uploadDescription, setUploadDescription] = useState("");
  const [businessDomain, setBusinessDomain] = useState("");
  const [query, setQuery] = useState("");
  const [error, setError] = useState("");
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    fetchSkills()
      .then(setSkills)
      .catch(() => setError(t("skills.loadFailed")));
  }, [t]);

  const visibleSkills = skills.filter((skill) =>
    `${skill.name} ${skill.skillKey} ${skill.description}`
      .toLowerCase()
      .includes(query.toLowerCase()),
  );

  const saveMetadata = async () => {
    if (!editing || saving) return;
    setSaving(true);
    setError("");
    try {
      const saved = await saveSkillMetadata(editing.id, {
        name: editing.name,
        description: editing.description,
        businessDomain: editing.businessDomain,
      });
      setSkills((current) =>
        current.map((skill) => (skill.id === saved.id ? saved : skill)),
      );
      setEditing(null);
    } catch {
      setError(t("skills.saveFailed"));
    } finally {
      setSaving(false);
    }
  };

  const importArchive = async (overwrite = false) => {
    if (!archive || !businessDomain.trim() || saving) return;
    setSaving(true);
    setError("");
    const form = new FormData();
    form.append("file", archive);
    form.append("name", uploadName);
    form.append("description", uploadDescription);
    form.append("businessDomain", businessDomain.trim());
    form.append("overwrite", String(overwrite));
    try {
      const saved = await importSkillArchive(form);
      setSkills((current) => [
        saved,
        ...current.filter((item) => item.id !== saved.id),
      ]);
      setUploadOpen(false);
      setArchive(null);
      setUploadName("");
      setUploadDescription("");
      setBusinessDomain("");
    } catch (failure) {
      if (failure instanceof SkillConflictError && !overwrite) {
        if (await confirm({ message: t("skills.overwriteConfirm") })) {
          setSaving(false);
          await importArchive(true);
          return;
        }
        return;
      }
      setError(
        failure instanceof Error && failure.message === "SKILL_MD_NOT_AT_ROOT"
          ? t("skills.skillMdNotAtRoot")
          : t("skills.importFailed"),
      );
    } finally {
      setSaving(false);
    }
  };

  const openSkill = async (skill: SkillItem) => {
    setViewing(skill);
    setSelectedFile(null);
    try {
      const manifest = await fetchSkillFiles(skill.id);
      setFiles(manifest);
      const first = manifest.find((file) => file.path === "SKILL.md") ?? manifest[0];
      if (first) await openFile(skill.id, first.path);
    } catch {
      setError(t("skills.filesFailed"));
    }
  };

  const openFile = async (skillId: string, path: string) => {
    setFileDraft(null);
    setFileError("");
    setFileSuccess("");
    try {
      setSelectedFile(await fetchSkillFile(skillId, path));
    } catch {
      /* keep previous selection */
    }
  };

  const saveFile = async () => {
    if (!viewing || !selectedFile || fileDraft === null || fileSaving) return;
    setFileSaving(true);
    setFileError("");
    setFileSuccess("");
    try {
      const saved = await saveSkillFile(viewing.id, {
        path: selectedFile.path,
        content: fileDraft,
        version: selectedFile.version,
      });
      setSelectedFile(saved);
      setFileDraft(null);
      setFileSuccess(t("skills.fileSaved", { version: saved.version }));
      const items = await fetchSkills();
      setSkills(items);
      setViewing(items.find((item) => item.id === viewing.id) ?? viewing);
    } catch (failure) {
      setFileError(
        failure instanceof SkillFileConflictError
          ? t("skills.fileConflict")
          : t("skills.fileSaveFailed"),
      );
    } finally {
      setFileSaving(false);
    }
  };

  const enableSkill = async (skill: SkillItem, enabled: boolean) => {
    try {
      const saved = await setSkillEnabled(skill.id, enabled);
      setSkills((current) =>
        current.map((item) => (item.id === saved.id ? saved : item)),
      );
    } catch {
      setError(t("skills.statusFailed"));
    }
  };

  const removeSkill = async (skill: SkillItem) => {
    if (!(await confirm({ message: t("skills.deleteConfirm", { name: skill.name }), dangerous: true })))
      return;
    try {
      await deleteSkill(skill.id);
      setSkills((current) => current.filter((item) => item.id !== skill.id));
    } catch {
      setError(t("skills.deleteFailed"));
    }
  };

  return (
    <>
      <Dialog />
      <PageHeader
        kicker="SKILL ASSETS / REPOSITORY"
        title={t("skills.title")}
        description={t("skills.description")}
        action={
          <Button
            onClick={() => {
              setError("");
              setUploadOpen(true);
            }}
          >
            ＋ {t("skills.create")}
          </Button>
        }
      />
      {error && <div className="skill-error">× {error}</div>}
      <section className="run-table skill-registry">
        <div className="table-tools">
          <label className="search-mini skill-search">
            ⌕
            <input
              value={query}
              onChange={(event) => setQuery(event.target.value)}
              placeholder={t("skills.search")}
            />
          </label>
        </div>
        <div className="table-head skill-table-row">
          <span>{t("skills.skill")}</span>
          <span>{t("skills.domain")}</span>
          <span>{t("skills.archive")}</span>
          <span>{t("skills.updated")}</span>
          <span>{t("skills.status")}</span>
          <span>{t("skills.actions")}</span>
        </div>
        {visibleSkills.length === 0 ? (
          <div className="skill-empty">
            <span>✦</span>
            <b>{t("skills.emptyTitle")}</b>
            <p>{t("skills.emptyDescription")}</p>
          </div>
        ) : (
          visibleSkills.map((skill) => (
            <div className="table-row skill-table-row" key={skill.id}>
              <span className="skill-identity">
                <i>✦</i>
                <span>
                  <b>{skill.name}</b>
                  <code>{skill.skillKey}</code>
                  <small>{skill.description}</small>
                </span>
              </span>
              <span className="skill-domain">#{skill.businessDomain}</span>
              <code>{skill.archiveName ?? "—"}</code>
              <span>
                {skill.updatedAt
                  ? new Date(skill.updatedAt).toLocaleString()
                  : "—"}
              </span>
              <Toggle
                on={skill.enabled}
                setOn={(next) => void enableSkill(skill, next)}
                label={`${t("skills.status")} ${skill.name}`}
              />
              <span className="model-actions">
                <button
                  className="link-button"
                  onClick={() => void openSkill(skill)}
                >
                  {t("skills.view")}
                </button>
                <button
                  className="link-button"
                  onClick={() => {
                    setError("");
                    setEditing(skill);
                  }}
                >
                  {t("skills.edit")}
                </button>
                <button
                  className="link-button danger-link"
                  onClick={() => void removeSkill(skill)}
                >
                  {t("skills.delete")}
                </button>
              </span>
            </div>
          ))
        )}
      </section>
      {uploadOpen &&
        createPortal(
          <div
            className="model-modal-mask"
            role="presentation"
            onMouseDown={() => setUploadOpen(false)}
          >
            <div
              className="form-surface model-editor skill-editor"
              role="dialog"
              aria-modal="true"
              aria-label={t("skills.importTitle")}
              onMouseDown={(event) => event.stopPropagation()}
            >
              <div className="form-title">
                <div>
                  <p className="kicker">SKILL PACKAGE / IMPORT</p>
                  <h2>{t("skills.importTitle")}</h2>
                </div>
                <button
                  className="link-button"
                  onClick={() => setUploadOpen(false)}
                >
                  {t("skills.close")} ×
                </button>
              </div>
              <div className="skill-import-strip">
                <div>
                  <b>{archive?.name ?? t("skills.noArchive")}</b>
                  <small>{t("skills.archiveHint")}</small>
                </div>
                <label className="ui-button quiet file-button">
                  {t("skills.selectFile")}
                  <input
                    type="file"
                    accept=".zip,application/zip"
                    onChange={(event) => {
                      const file = event.target.files?.[0];
                      if (file) setArchive(file);
                    }}
                  />
                </label>
              </div>
              <div className="field-grid">
                <label className="field">
                  <span>{t("skills.name")}</span>
                  <input
                    value={uploadName}
                    onChange={(event) => setUploadName(event.target.value)}
                    placeholder={t("skills.parsedPlaceholder")}
                  />
                </label>
                <label className="field">
                  <span>{t("skills.skillDescription")}</span>
                  <input
                    value={uploadDescription}
                    onChange={(event) =>
                      setUploadDescription(event.target.value)
                    }
                    placeholder={t("skills.parsedPlaceholder")}
                  />
                </label>
                <label className="field">
                  <span>{t("skills.domain")}</span>
                  <input
                    value={businessDomain}
                    onChange={(event) => setBusinessDomain(event.target.value)}
                    placeholder={t("skills.domainPlaceholder")}
                  />
                </label>
              </div>
              {error && (
                <div className="skill-error modal-error">× {error}</div>
              )}
              <div className="sticky-actions">
                <Button quiet onClick={() => setUploadOpen(false)}>
                  {t("skills.cancel")}
                </Button>
                <Button
                  onClick={() => void importArchive()}
                  disabled={saving || !archive || !businessDomain.trim()}
                >
                  {saving ? t("skills.parsing") : t("skills.import")}
                </Button>
              </div>
            </div>
          </div>,
          document.body,
        )}
      {viewing &&
        createPortal(
          <div
            className="model-modal-mask"
            role="presentation"
            onMouseDown={() => setViewing(null)}
          >
            <div
              className="skill-browser"
              role="dialog"
              aria-modal="true"
              onMouseDown={(event) => event.stopPropagation()}
            >
              <header>
                <div>
                  <p className="kicker">SKILL PACKAGE / EXPLORER</p>
                  <h2>{viewing.name}</h2>
                  <span className="skill-domain">
                    #{viewing.businessDomain}
                  </span>
                </div>
                <button
                  className="link-button"
                  onClick={() => setViewing(null)}
                >
                  {t("skills.close")} ×
                </button>
              </header>
              <div className="skill-browser-body">
                <aside>
                  <p>
                    {t("skills.files")} · {files.length}
                  </p>
                  <SkillTree
                    nodes={buildSkillTree(files)}
                    selectedPath={selectedFile?.path}
                    onSelect={(path) => void openFile(viewing.id, path)}
                  />
                </aside>
                <main>
                  <div className="file-preview-head">
                    <div>
                      <code>{selectedFile?.path ?? "—"}</code>
                      <small>
                        {selectedFile
                          ? `${selectedFile.mediaType} · ${selectedFile.size} B`
                          : ""}
                      </small>
                    </div>
                    {selectedFile?.previewable && fileDraft === null && (
                      <button
                        className="file-edit-button"
                        onClick={() => {
                          setFileDraft(selectedFile.content ?? "");
                          setFileSuccess("");
                        }}
                      >
                        ✎ {t("skills.editFile")}
                      </button>
                    )}
                    {fileDraft !== null && (
                      <div className="file-edit-actions">
                        <button
                          onClick={() => {
                            setFileDraft(null);
                            setFileError("");
                          }}
                        >
                          {t("skills.cancel")}
                        </button>
                        <button
                          className="primary"
                          onClick={() => void saveFile()}
                          disabled={fileSaving}
                        >
                          {fileSaving
                            ? t("skills.saving")
                            : t("skills.saveFile")}
                        </button>
                      </div>
                    )}
                  </div>
                  {fileError && (
                    <div className="file-edit-error">× {fileError}</div>
                  )}
                  {fileSuccess && (
                    <div className="file-edit-success" role="status">
                      ✓ {fileSuccess}
                    </div>
                  )}
                  {fileDraft !== null ? (
                    <textarea
                      className="skill-file-editor"
                      value={fileDraft}
                      onChange={(event) => setFileDraft(event.target.value)}
                      spellCheck={false}
                    />
                  ) : selectedFile?.previewable ? (
                    <pre>{selectedFile.content}</pre>
                  ) : (
                    <div className="binary-preview">
                      {t("skills.binaryPreview")}
                    </div>
                  )}
                </main>
              </div>
            </div>
          </div>,
          document.body,
        )}
      {editing &&
        createPortal(
          <div
            className="model-modal-mask"
            role="presentation"
            onMouseDown={() => setEditing(null)}
          >
            <div
              className="form-surface model-editor skill-editor"
              role="dialog"
              onMouseDown={(event) => event.stopPropagation()}
            >
              <div className="form-title">
                <div>
                  <p className="kicker">SKILL METADATA / EDIT</p>
                  <h2>{editing.name}</h2>
                </div>
                <button
                  className="link-button"
                  onClick={() => setEditing(null)}
                >
                  {t("skills.close")} ×
                </button>
              </div>
              <div className="field-grid">
                <label className="field">
                  <span>{t("skills.name")}</span>
                  <input
                    value={editing.name}
                    onChange={(event) =>
                      setEditing({ ...editing, name: event.target.value })
                    }
                  />
                </label>
                <label className="field">
                  <span>{t("skills.domain")}</span>
                  <input
                    value={editing.businessDomain}
                    onChange={(event) =>
                      setEditing({
                        ...editing,
                        businessDomain: event.target.value,
                      })
                    }
                  />
                </label>
                <label className="field wide">
                  <span>{t("skills.skillDescription")}</span>
                  <input
                    value={editing.description}
                    onChange={(event) =>
                      setEditing({
                        ...editing,
                        description: event.target.value,
                      })
                    }
                  />
                </label>
              </div>
              <div className="sticky-actions">
                <Button quiet onClick={() => setEditing(null)}>
                  {t("skills.cancel")}
                </Button>
                <Button onClick={() => void saveMetadata()} disabled={saving}>
                  {saving ? t("skills.saving") : t("skills.save")}
                </Button>
              </div>
            </div>
          </div>,
          document.body,
        )}
    </>
  );
}
