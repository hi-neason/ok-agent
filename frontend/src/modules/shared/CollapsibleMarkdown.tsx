import { useState } from "react";
import { Markdown } from "./Markdown";

/**
 * 长 Markdown 折叠组件：超过 `THRESHOLD` 字符（默认 360）的 assistant 回复默认折叠，
 * 露出约 12em 高度的内容预览并渐隐遮挡底部；点「展开 ▾」展开全文，「收叠 ▴」复位。
 *
 * 单一组件实例自带 useState，不污染父级；每条消息对应一个实例（map index 作 key）。
 */

const COLLAPSE_HEIGHT_EM = 12;
const COLLAPSE_THRESHOLD = 360;

export function CollapsibleMarkdown({ source }: { source: string }) {
  const collapsible = source.length > COLLAPSE_THRESHOLD;
  const [expanded, setExpanded] = useState(!collapsible);

  // 内容不够长：直接当 Markdown 渲染，不显示折叠按钮
  if (!collapsible) {
    return <Markdown source={source} />;
  }

  return (
    <div className="collapsible-md">
      <div
        className={`collapsible-md-body ${expanded ? "expanded" : "collapsed"}`}
      >
        <Markdown source={source} />
      </div>
      <div className="collapsible-md-actions">
        <button
          type="button"
          className="link-button collapsible-md-toggle"
          onClick={() => setExpanded((v) => !v)}
          aria-expanded={expanded}
        >
          {expanded ? "收叠 ▴" : "展开 ▾"}
        </button>
      </div>
    </div>
  );
}

/**
 * 单纯暴露折叠最大高度的常量，便于外层 CSS 用同一来源。
 * 这里仅做占位 — CSS 直接 `max-height: 12em` 也行；保留是为了便于后续接入测量逻辑。
 */
export const COLLAPSIBLE_MAX_HEIGHT = `${COLLAPSE_HEIGHT_EM}em`;
