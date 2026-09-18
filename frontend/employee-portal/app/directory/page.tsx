import Link from "next/link";
import { apiClient, ApiError } from "@/lib/api/client";
import type { EmployeeSummary, PageResponse } from "@/lib/api/types";
import { AuthRequiredNotice } from "@/components/AuthRequiredNotice";
import { PermissionDeniedNotice } from "@/components/PermissionDeniedNotice";
import { EmptyState } from "@/components/EmptyState";
import { Pagination } from "@/components/Pagination";

interface DirectorySearchParams {
  query?: string;
  status?: string;
  page?: string;
}

function SearchForm({ query, status }: { query: string; status: string }) {
  return (
    <form method="get" role="search" aria-label="Search employee directory" className="search-form">
      <div>
        <label htmlFor="query">Name or employee number</label>
        <input id="query" name="query" type="search" defaultValue={query} />
      </div>
      <div>
        <label htmlFor="status">Status</label>
        <select id="status" name="status" defaultValue={status}>
          <option value="">All</option>
          <option value="ACTIVE">Active</option>
          <option value="INACTIVE">Inactive</option>
        </select>
      </div>
      <button type="submit">Search</button>
    </form>
  );
}

export default async function DirectoryPage({ searchParams }: { searchParams: Promise<DirectorySearchParams> }) {
  const params = await searchParams;
  const page = Number.parseInt(params.page ?? "0", 10) || 0;
  const query = params.query?.trim() ?? "";
  const status = params.status ?? "";

  const search = new URLSearchParams();
  if (query) search.set("query", query);
  if (status) search.set("status", status);
  search.set("page", String(page));
  search.set("size", "20");

  try {
    const result = await apiClient.get<PageResponse<EmployeeSummary>>(`/api/v1/employees?${search.toString()}`);
    return (
      <section aria-labelledby="directory-heading">
        <h2 id="directory-heading">Employee directory</h2>
        <SearchForm query={query} status={status} />
        {result.items.length === 0 ? (
          <EmptyState message="No employees match your search." />
        ) : (
          <>
            <ul className="directory-list">
              {result.items.map((employee) => (
                <li key={employee.id}>
                  <Link href={`/directory/${employee.id}`}>
                    {employee.firstName} {employee.lastName}
                  </Link>
                  <span className="directory-meta">
                    {employee.employeeNumber} &middot; {employee.status}
                  </span>
                </li>
              ))}
            </ul>
            <Pagination
              basePath="/directory"
              currentPage={result.page.number}
              pageSize={result.page.size}
              total={result.page.total}
              extraParams={{ query, status }}
            />
          </>
        )}
      </section>
    );
  } catch (error) {
    if (error instanceof ApiError && error.status === 401) {
      return <AuthRequiredNotice />;
    }
    if (error instanceof ApiError && error.status === 403) {
      return (
        <PermissionDeniedNotice message="You need team or organization-wide access to browse the directory." />
      );
    }
    throw error;
  }
}
