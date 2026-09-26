import Link from "next/link";
import { apiClient, ApiError } from "@/lib/api/client";
import type { EmployeeSummary, PageResponse } from "@/lib/api/types";
import { AuthRequiredNotice } from "@/components/AuthRequiredNotice";
import { PermissionDeniedNotice } from "@/components/PermissionDeniedNotice";
import { EmptyState } from "@/components/EmptyState";
import { Pagination } from "@/components/Pagination";
import { PageHeader } from "@/components/PageHeader";

interface DirectorySearchParams {
  query?: string;
  status?: string;
  page?: string;
}

function initialsFor(first: string, last: string): string {
  return `${first[0] ?? ""}${last[0] ?? ""}`.toUpperCase();
}

function SearchForm({ query, status }: { query: string; status: string }) {
  return (
    <form method="get" role="search" aria-label="Search employee directory" className="search-form">
      <div>
        <label htmlFor="query">Name or employee number</label>
        <input id="query" name="query" type="search" defaultValue={query} placeholder="e.g. Priya Sharma" />
      </div>
      <div>
        <label htmlFor="status">Status</label>
        <select id="status" name="status" defaultValue={status}>
          <option value="">All</option>
          <option value="ACTIVE">Active</option>
          <option value="INACTIVE">Inactive</option>
        </select>
      </div>
      <button type="submit" className="btn btn-primary">
        Search
      </button>
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
        <PageHeader title="Employee Directory" description="Search and browse colleagues across GDB." />
        <div className="card">
          <SearchForm query={query} status={status} />
          {result.items.length === 0 ? (
            <EmptyState message="No employees match your search." icon="directory" />
          ) : (
            <>
              <ul className="directory-list">
                {result.items.map((employee) => (
                  <li key={employee.id}>
                    <Link href={`/directory/${employee.id}`} className="employee-card">
                      <div className="list-row-main">
                        <span className="avatar">{initialsFor(employee.firstName, employee.lastName)}</span>
                        <div>
                          <div className="list-row-title">
                            {employee.firstName} {employee.lastName}
                          </div>
                          <div className="directory-meta">{employee.employeeNumber}</div>
                        </div>
                      </div>
                      <span className={`badge ${employee.status === "ACTIVE" ? "badge-success" : "badge-neutral"}`}>
                        {employee.status}
                      </span>
                    </Link>
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
        </div>
      </section>
    );
  } catch (error) {
    if (error instanceof ApiError && error.status === 401) {
      return (
        <section aria-labelledby="directory-heading">
          <PageHeader title="Employee Directory" />
          <AuthRequiredNotice />
        </section>
      );
    }
    if (error instanceof ApiError && error.status === 403) {
      return (
        <section aria-labelledby="directory-heading">
          <PageHeader title="Employee Directory" />
          <PermissionDeniedNotice message="You need team or organization-wide access to browse the directory." />
        </section>
      );
    }
    throw error;
  }
}
