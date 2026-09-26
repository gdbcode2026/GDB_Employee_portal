export function SkeletonLine({ width = "100%" }: { width?: string }) {
  return <div className="skeleton skeleton-line" style={{ width }} />;
}

export function SkeletonCard() {
  return <div className="skeleton skeleton-card" />;
}

export function PageSkeleton({ cards = 3 }: { cards?: number }) {
  return (
    <div>
      <div className="skeleton skeleton-line" style={{ width: "14rem", height: "1.6rem", marginBottom: "1.5rem" }} />
      <div className="skeleton-stack card-grid grid-3">
        {Array.from({ length: cards }).map((_, index) => (
          <SkeletonCard key={index} />
        ))}
      </div>
    </div>
  );
}
