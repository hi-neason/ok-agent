import { createPortal } from "react-dom";
import { useTranslation } from "react-i18next";

export type ConfirmOptions = {
  title?: string;
  message: string;
  confirmText?: string;
  cancelText?: string;
  dangerous?: boolean;
};

export function ConfirmDialog({
  open,
  options,
  onClose,
}: {
  open: boolean;
  options: ConfirmOptions | null;
  onClose: (result: boolean) => void;
}) {
  const { t } = useTranslation();
  if (!open || !options) return null;

  const { title, message, confirmText, cancelText, dangerous } = options;

  return createPortal(
    <div
      className="confirm-mask"
      onMouseDown={() => onClose(false)}
      role="presentation"
    >
      <div
        className="confirm-panel"
        role="alertdialog"
        aria-modal="true"
        aria-live="assertive"
        onMouseDown={(e) => e.stopPropagation()}
      >
        {title && (
          <div className="confirm-header">
            <h3>{title}</h3>
          </div>
        )}
        <div className="confirm-body">
          <p>{message}</p>
        </div>
        <div className="confirm-footer">
          <button
            className="ui-button quiet"
            onClick={() => onClose(false)}
            type="button"
          >
            {cancelText ?? t("common.cancel")}
          </button>
          <button
            className={`ui-button ${dangerous ? "danger" : ""}`}
            onClick={() => onClose(true)}
            type="button"
          >
            {confirmText ?? t("common.confirm")}
          </button>
        </div>
      </div>
    </div>,
    document.body,
  );
}
