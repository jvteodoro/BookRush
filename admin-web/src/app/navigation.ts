export const adminNavigation = [
  { to: "/", label: "Overview" },
  { to: "/catalog", label: "Catalog & Import" },
  { to: "/review", label: "Review Queue" },
  { to: "/analytics", label: "Analytics" },
  { to: "/feed-lab", label: "Feed Lab" },
  { to: "/training", label: "Training" },
  { to: "/operations", label: "Users & moderation" },
] as const;

export function isAdminRoute(pathname: string) {
  return adminNavigation.some(({ to }) => to === pathname);
}
