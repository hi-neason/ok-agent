import { useTranslation } from "react-i18next";

export type Page<T> = {
  content: T[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
};

type PaginationProps = {
  page: number;
  totalPages: number;
  totalElements: number;
  size: number;
  loading?: boolean;
  onPageChange: (page: number) => void;
  onSizeChange?: (size: number) => void;
};

export function Pagination({
  page,
  totalPages,
  totalElements,
  size,
  loading,
  onPageChange,
  onSizeChange,
}: PaginationProps) {
  const { t } = useTranslation();

  if (totalElements === 0) {
    return null;
  }
  if (!onSizeChange && totalPages <= 1 && totalElements <= size) {
    return null;
  }

  return (
    <div className="pagination-bar">
      <span className="pagination-total">
        {t("pagination.total", { total: totalElements })}
      </span>
      <div className="pagination-controls">
        <button
          className="filter-chip"
          disabled={page === 0 || loading}
          onClick={() => onPageChange(Math.max(0, page - 1))}
        >
          {t("pagination.prev")}
        </button>
        <span className="pagination-current">
          {t("pagination.pageInfo", {
            page: page + 1,
            totalPages: Math.max(1, totalPages),
          })}
        </span>
        <button
          className="filter-chip"
          disabled={page + 1 >= totalPages || loading}
          onClick={() => onPageChange(page + 1)}
        >
          {t("pagination.next")}
        </button>
        {onSizeChange && (
          <select
            className="filter-chip pagination-size"
            value={size}
            onChange={(e) => onSizeChange(Number(e.target.value))}
            disabled={loading}
          >
            {[10, 20, 50].map((s) => (
              <option key={s} value={s}>
                {t("pagination.pageSize", { size: s })}
              </option>
            ))}
          </select>
        )}
      </div>
    </div>
  );
}
