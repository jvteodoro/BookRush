import type { ReportingResponse } from "../../lib/services";
import { statusText } from "./reportingStatus";

export function EmptyReportState({ data }: { data: ReportingResponse }) {
  return <div className="dashboard-state">{statusText(data.status)}</div>;
}
