import {
  useCallback,
  useEffect,
  useRef,
  useState,
  type ReactNode,
} from "react";
import { useTranslation } from "react-i18next";

const STORAGE_KEY = "ok-agent.config-width";
const DEFAULT_WIDTH = 54;
const MIN_WIDTH = 34;
const MAX_WIDTH = 72;

function clamp(value: number, lo: number, hi: number): number {
  return Math.min(hi, Math.max(lo, value));
}

function useIsVertical(): boolean {
  const [vertical, setVertical] = useState(
    () =>
      typeof window !== "undefined" &&
      window.matchMedia("(max-width: 900px)").matches,
  );
  useEffect(() => {
    const mq = window.matchMedia("(max-width: 900px)");
    const handler = (e: MediaQueryListEvent) => setVertical(e.matches);
    mq.addEventListener("change", handler);
    return () => mq.removeEventListener("change", handler);
  }, []);
  return vertical;
}

/**
 * Two-pane layout (configuration workbench + debug panel) with a draggable
 * separator. The width ratio is persisted to localStorage, double-click resets
 * it to the default, and each pane can be collapsed independently.
 */
export function ResizableWorkbench({
  left,
  right,
}: {
  left: ReactNode;
  right: ReactNode;
}) {
  const { t } = useTranslation();
  const [width, setWidth] = useState<number>(() => {
    const raw = Number(window.localStorage.getItem(STORAGE_KEY));
    return Number.isFinite(raw) && raw >= MIN_WIDTH && raw <= MAX_WIDTH
      ? raw
      : DEFAULT_WIDTH;
  });
  const [leftHidden, setLeftHidden] = useState(false);
  const [rightHidden, setRightHidden] = useState(false);
  const [dragging, setDragging] = useState(false);
  const layoutRef = useRef<HTMLDivElement>(null);
  const vertical = useIsVertical();

  useEffect(() => {
    window.localStorage.setItem(STORAGE_KEY, String(width));
  }, [width]);

  useEffect(() => {
    if (!dragging) return;
    const previous = document.body.style.userSelect;
    document.body.style.userSelect = "none";
    return () => {
      document.body.style.userSelect = previous;
    };
  }, [dragging]);

  const applyAt = useCallback(
    (clientX: number, clientY: number) => {
      const bounds = layoutRef.current?.getBoundingClientRect();
      if (!bounds) return;
      if (vertical) {
        const pct = ((clientY - bounds.top) / bounds.height) * 100;
        setWidth(clamp(pct, 25, 80));
      } else {
        const pct = ((clientX - bounds.left) / bounds.width) * 100;
        setWidth(clamp(pct, MIN_WIDTH, MAX_WIDTH));
      }
    },
    [vertical],
  );

  const onPointerDown = (event: React.PointerEvent) => {
    event.currentTarget.setPointerCapture(event.pointerId);
    setDragging(true);
    const move = (moveEvent: PointerEvent) => applyAt(moveEvent.clientX, moveEvent.clientY);
    const up = () => {
      setDragging(false);
      window.removeEventListener("pointermove", move);
      window.removeEventListener("pointerup", up);
    };
    window.addEventListener("pointermove", move);
    window.addEventListener("pointerup", up);
  };

  const onKeyDown = (event: React.KeyboardEvent) => {
    if (event.key === "ArrowLeft") setWidth((v) => clamp(v - 2, MIN_WIDTH, MAX_WIDTH));
    if (event.key === "ArrowRight") setWidth((v) => clamp(v + 2, MIN_WIDTH, MAX_WIDTH));
  };

  const onDoubleClick = () => setWidth(DEFAULT_WIDTH);

  const template =
    vertical && !leftHidden && !rightHidden
      ? `${width}fr 10px ${100 - width}fr`
      : undefined;
  const gridStyle = vertical
    ? { gridTemplateRows: template, gridTemplateColumns: "1fr" }
    : {
        gridTemplateColumns: `${leftHidden ? "0" : `minmax(0, ${width}fr)`} 10px ${
          rightHidden ? "0" : `minmax(0, ${100 - width}fr)`
        }`,
      };

  return (
    <div
      className={`agent-config-layout${dragging ? " is-dragging" : ""}${
        vertical ? " vertical" : ""
      }${leftHidden ? " left-hidden" : ""}${rightHidden ? " right-hidden" : ""}`}
      ref={layoutRef}
      style={gridStyle}
    >
      {leftHidden ? (
        <div className="agent-pane-collapsed agent-pane-collapsed--left">
          <button
            type="button"
            className="link-button"
            onClick={() => setLeftHidden(false)}
            title={t("agents.expandConfig")}
          >
            ›
          </button>
        </div>
      ) : (
        <div className="agent-config-workbench">{left}</div>
      )}

      {!vertical && (
        <div
          className={`agent-panel-resizer${dragging ? " dragging" : ""}`}
          role="separator"
          aria-orientation="vertical"
          aria-label={t("agents.resizePanels")}
          tabIndex={0}
          onPointerDown={onPointerDown}
          onKeyDown={onKeyDown}
          onDoubleClick={onDoubleClick}
        >
          <i />
          <button
            type="button"
            className="resizer-collapse resizer-collapse--left"
            title={t("agents.collapseConfig")}
            onClick={() => setLeftHidden(true)}
          >
            ‹
          </button>
          <button
            type="button"
            className="resizer-collapse resizer-collapse--right"
            title={t("agents.collapseDebug")}
            onClick={() => setRightHidden(true)}
          >
            ›
          </button>
        </div>
      )}

      {rightHidden ? (
        <div className="agent-pane-collapsed agent-pane-collapsed--right">
          <button
            type="button"
            className="link-button"
            onClick={() => setRightHidden(false)}
            title={t("agents.expandDebug")}
          >
            ‹
          </button>
        </div>
      ) : (
        <aside className="agent-chat-panel">{right}</aside>
      )}
    </div>
  );
}
