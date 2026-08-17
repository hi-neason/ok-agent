import { useCallback, useRef, useState } from "react";
import { ConfirmDialog, type ConfirmOptions } from "./ConfirmDialog";

export function useConfirm() {
  const resolveRef = useRef<((value: boolean) => void) | null>(null);
  const [open, setOpen] = useState(false);
  const [options, setOptions] = useState<ConfirmOptions | null>(null);

  const confirm = useCallback((opts: ConfirmOptions): Promise<boolean> => {
    return new Promise((resolve) => {
      resolveRef.current = resolve;
      setOptions(opts);
      setOpen(true);
    });
  }, []);

  const onClose = useCallback((result: boolean) => {
    setOpen(false);
    setOptions(null);
    if (resolveRef.current) {
      resolveRef.current(result);
      resolveRef.current = null;
    }
  }, []);

  const Dialog = useCallback(() => {
    return <ConfirmDialog open={open} options={options} onClose={onClose} />;
  }, [open, options, onClose]);

  return { confirm, Dialog };
}
