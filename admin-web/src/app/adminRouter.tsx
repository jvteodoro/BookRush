import { createBrowserRouter } from "react-router-dom";
import { AnalyticsPage } from "../features/analytics/AnalyticsPage";
import { CatalogPage } from "../features/catalog/CatalogPage";
import { FeedLabPage } from "../features/feedlab/FeedLabPage";
import { ReviewPage } from "../features/review/ReviewPage";
import { TrainingPage } from "../features/training/TrainingPage";
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
          { path: "feed-lab", element: <FeedLabPage /> },
          { path: "training", element: <TrainingPage /> },
        ],
      },
    ],
    { basename: config.basePath.replace(/\/$/, "") || undefined },
  );
}
