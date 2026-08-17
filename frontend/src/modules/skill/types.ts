export type SkillItem = {
  id: string;
  skillKey: string;
  name: string;
  description: string;
  businessDomain: string;
  archiveName: string | null;
  archiveSize: number;
  enabled: boolean;
  updatedAt?: string;
};

export type SkillFileItem = {
  path: string;
  mediaType: string;
  size: number;
};

export type SkillFileContent = SkillFileItem & {
  previewable: boolean;
  content: string | null;
  version: number;
  updatedAt: string;
};

export type SkillTreeNode = {
  name: string;
  path: string;
  file?: SkillFileItem;
  children: SkillTreeNode[];
};

export function buildSkillTree(files: SkillFileItem[]): SkillTreeNode[] {
  const root: SkillTreeNode[] = [];
  files.forEach((file) => {
    let level = root;
    let path = "";
    file.path.split("/").forEach((name, index, segments) => {
      path = path ? `${path}/${name}` : name;
      let node = level.find((item) => item.name === name);
      if (!node) {
        node = { name, path, children: [] };
        level.push(node);
      }
      if (index === segments.length - 1) node.file = file;
      level = node.children;
    });
  });
  return root;
}
