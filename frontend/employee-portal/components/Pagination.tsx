import Link from "next/link";

interface PaginationProps {
  basePath: string;
  currentPage: number;
  pageSize: number;
  total: number;
  extraParams?: Record<string, string>;
}

export function Pagination({ basePath, currentPage, pageSize, total, extraParams = {} }: PaginationProps) {
  const totalPages = Math.max(1, Math.ceil(total / pageSize));
  if (totalPages <= 1) {
    return null;
  }

  function hrefFor(page: number): string {
    const params = new URLSearchParams();
    for (const [key, value] of Object.entries(extraParams)) {
      if (value) {
        params.set(key, value);
      }
    }
    params.set("page", String(page));
    return `${basePath}?${params.toString()}`;
  }

  const hasPrevious = currentPage > 0;
  const hasNext = currentPage + 1 < totalPages;

  return (
    <nav aria-label="Directory pages" className="pagination">
      {hasPrevious ? (
        <Link href={hrefFor(currentPage - 1)} rel="prev">
          Previous
        </Link>
      ) : (
        <span aria-disabled="true">Previous</span>
      )}
      <span aria-current="page">
        Page {currentPage + 1} of {totalPages}
      </span>
      {hasNext ? (
        <Link href={hrefFor(currentPage + 1)} rel="next">
          Next
        </Link>
      ) : (
        <span aria-disabled="true">Next</span>
      )}
    </nav>
  );
}
