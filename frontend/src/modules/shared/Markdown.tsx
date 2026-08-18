import { memo } from "react";
import ReactMarkdown from "react-markdown";
import remarkGfm from "remark-gfm";

/**
 * 安全渲染 Markdown 文本的轻量包装。
 *
 * - 用 react-markdown（默认不解析 raw HTML，天然防 XSS）；
 * - remark-gfm 启用 GitHub 风格 Markdown：表格 `| ... |`、任务列表、删除线；
 * - 所有外链强制 `target="_blank" rel="noopener noreferrer"`；
 * - 用 `memo` 包裹，长会话回放时避免无谓重渲染。
 */
const components = {
  a: ({ href, children, ...rest }: { href?: string; children?: React.ReactNode }) => (
    <a
      href={href}
      target="_blank"
      rel="noopener noreferrer"
      {...rest}
    >
      {children}
    </a>
  ),
};

export const Markdown = memo(function Markdown({ source }: { source: string }) {
  return (
    <ReactMarkdown remarkPlugins={[remarkGfm]} components={components}>
      {source}
    </ReactMarkdown>
  );
});
