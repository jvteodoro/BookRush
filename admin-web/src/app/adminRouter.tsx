import { lazy, Suspense } from "react";
import { createBrowserRouter } from "react-router-dom";
import type { ComponentType, LazyExoticComponent } from "react";
import { Layout } from "../components/Layout";
import { config } from "../lib/config";
import { Dashboard } from "./Dashboard";

const CatalogPage = lazy(() =>
  import("../features/catalog/CatalogPage").then((m) => ({
    default: m.CatalogPage,
  })),
);
const ReviewPage = lazy(() =>
  import("../features/review/ReviewPage").then((m) => ({
    default: m.ReviewPage,
  })),
);
const AnalyticsPage = lazy(() =>
  import("../features/analytics/AnalyticsPage").then((m) => ({
    default: m.AnalyticsPage,
  })),
);
const AnalyticsJobPage = lazy(() =>
  import("../features/analytics/AnalyticsJobPage").then((m) => ({
    default: m.AnalyticsJobPage,
  })),
);
const AnalyticsItemPage = lazy(() =>
  import("../features/analytics/AnalyticsItemPage").then((m) => ({
    default: m.AnalyticsItemPage,
  })),
);
const FeedLabPage = lazy(() =>
  import("../features/feedlab/FeedLabPage").then((m) => ({
    default: m.FeedLabPage,
  })),
);
const TrainingPage = lazy(() =>
  import("../features/training/TrainingPage").then((m) => ({
    default: m.TrainingPage,
  })),
);
const OperationsPage = lazy(() =>
  import("../features/operations/OperationsPage").then((m) => ({
    default: m.OperationsPage,
  })),
);
const DashboardHub = lazy(() =>
  import("../features/dashboards/DashboardHub").then((m) => ({
    default: m.DashboardHub,
  })),
);

function lazyElement(Page: LazyExoticComponent<ComponentType>) {
  return (
    <Suspense
      fallback={
        <div className="panel" role="status" aria-live="polite">
          Carregando página…
        </div>
      }
    >
      <Page />
    </Suspense>
  );
}

export function createAdminRouter() {
  return createBrowserRouter(
    [
      {
        path: "/",
        element: <Layout />,
        children: [
          { index: true, element: <Dashboard /> },
          { path: "catalog", element: lazyElement(CatalogPage) },
          { path: "review", element: lazyElement(ReviewPage) },
          { path: "analytics", element: lazyElement(AnalyticsPage) },
          {
            path: "analytics/jobs/:jobId",
            element: lazyElement(AnalyticsJobPage),
          },
          {
            path: "analytics/jobs/:jobId/items/:itemId",
            element: lazyElement(AnalyticsItemPage),
          },
          { path: "feed-lab", element: lazyElement(FeedLabPage) },
          { path: "training", element: lazyElement(TrainingPage) },
          { path: "operations", element: lazyElement(OperationsPage) },
          { path: "dashboards", element: lazyElement(DashboardHub) },
        ],
      },
    ],
    { basename: config.basePath.replace(/\/$/, "") || undefined },
  );
}
