import { createBrowserRouter } from "react-router-dom";
import { AnalyticsPage } from "../features/analytics/AnalyticsPage";
import { AnalyticsJobPage } from "../features/analytics/AnalyticsJobPage";
import { AnalyticsItemPage } from "../features/analytics/AnalyticsItemPage";
import { CatalogPage } from "../features/catalog/CatalogPage";
import { FeedLabPage } from "../features/feedlab/FeedLabPage";
import { ReviewPage } from "../features/review/ReviewPage";
import { TrainingPage } from "../features/training/TrainingPage";
import { OperationsPage } from "../features/operations/OperationsPage";
import { Layout } from "../components/Layout";
import { config } from "../lib/config";
import { Dashboard } from "./Dashboard";

export function createAdminRouter() {
  return createBrowserRouter(
    [
      {
        path: "/",
        element: <Layout />,
        children: [
          { index: true, element: <Dashboard /> },
          { path: "catalog", element: <CatalogPage /> },
          { path: "review", element: <ReviewPage /> },
          { path: "analytics", element: <AnalyticsPage /> },
          { path: "analytics/jobs/:jobId", element: <AnalyticsJobPage /> },
          {
            path: "analytics/jobs/:jobId/items/:itemId",
            element: <AnalyticsItemPage />,
          },
          { path: "feed-lab", element: <FeedLabPage /> },
          { path: "training", element: <TrainingPage /> },
          { path: "operations", element: <OperationsPage /> },
        ],
      },
    ],
    { basename: config.basePath.replace(/\/$/, "") || undefined },
  );
}
