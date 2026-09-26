import { apiClient, ApiError } from "@/lib/api/client";
import type { PageResponse, Project, ProjectTask } from "@/lib/api/types";
import { AuthRequiredNotice } from "@/components/AuthRequiredNotice";
import { PageHeader } from "@/components/PageHeader";
import { StatCard } from "@/components/StatCard";
import { StatusBadge } from "@/components/StatusBadge";
import { EmptyState } from "@/components/EmptyState";
import { formatDate } from "@/lib/format";

async function safe<T>(promise: Promise<T>): Promise<T | null> {
  try {
    return await promise;
  } catch {
    return null;
  }
}

export default async function ProjectsPage() {
  let tasks: PageResponse<ProjectTask>;
  try {
    tasks = await apiClient.get<PageResponse<ProjectTask>>("/api/v1/tasks/me?size=25&sort=dueDate");
  } catch (error) {
    if (error instanceof ApiError && error.status === 401) {
      return (
        <section aria-labelledby="projects-heading">
          <PageHeader title="Projects & Tasks" />
          <AuthRequiredNotice />
        </section>
      );
    }
    throw error;
  }

  const projects = await safe(apiClient.get<PageResponse<Project>>("/api/v1/projects?size=50"));
  const projectById = new Map((projects?.items ?? []).map((project) => [project.id, project]));

  const openTasks = tasks.items.filter((task) => task.status !== "DONE" && task.status !== "CANCELLED");
  const doneTasks = tasks.items.filter((task) => task.status === "DONE");

  return (
    <section aria-labelledby="projects-heading">
      <PageHeader title="Projects & Tasks" description="Tasks assigned to you across all projects." />

      <div className="card-grid grid-3">
        <StatCard label="Open tasks" value={String(openTasks.length)} icon="projects" />
        <StatCard label="Completed" value={String(doneTasks.length)} icon="checkCircle" />
        <StatCard label="Projects you're on" value={projects ? String(projects.items.length) : "Unavailable"} icon="building" />
      </div>

      <div className="card" style={{ marginTop: "1.25rem" }}>
        <div className="card-header">
          <h2>Your tasks</h2>
        </div>
        {tasks.items.length === 0 ? (
          <EmptyState message="No tasks are assigned to you." icon="projects" />
        ) : (
          <table className="data-table">
            <thead>
              <tr>
                <th>Task</th>
                <th>Project</th>
                <th>Priority</th>
                <th>Due</th>
                <th>Status</th>
              </tr>
            </thead>
            <tbody>
              {tasks.items.map((task) => (
                <tr key={task.id}>
                  <td>{task.title}</td>
                  <td>{projectById.get(task.projectId)?.name ?? "—"}</td>
                  <td>{task.priority}</td>
                  <td>{formatDate(task.dueDate)}</td>
                  <td>
                    <StatusBadge status={task.status} />
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </div>

      <div className="card" style={{ marginTop: "1.25rem" }}>
        <div className="card-header">
          <h2>Your projects</h2>
        </div>
        {!projects || projects.items.length === 0 ? (
          <EmptyState message="No projects are visible to you yet." icon="building" />
        ) : (
          <ul style={{ listStyle: "none", padding: 0, margin: 0 }}>
            {projects.items.map((project) => (
              <li key={project.id} className="list-row">
                <div className="list-row-main">
                  <div>
                    <div className="list-row-title">{project.name}</div>
                    <div className="list-row-sub">{project.code}</div>
                  </div>
                </div>
                <div className="list-row-end">
                  <StatusBadge status={project.status} />
                </div>
              </li>
            ))}
          </ul>
        )}
      </div>
    </section>
  );
}
