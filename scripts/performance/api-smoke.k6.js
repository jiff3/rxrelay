import http from "k6/http";
import { check, sleep } from "k6";

const BASE_URL = __ENV.BASE_URL || "http://host.docker.internal:8080";

export const options = {
  vus: Number(__ENV.VUS || 4),
  duration: __ENV.DURATION || "15s",
  summaryTrendStats: ["min", "med", "avg", "p(90)", "p(95)", "p(99)", "max"],
  thresholds: {
    checks: ["rate==1"],
    http_req_failed: ["rate==0"],
  },
};

export function setup() {
  const response = http.get(`${BASE_URL}/api/v1/drugs?size=1`, {
    tags: { endpoint: "discovery" },
  });
  const valid = check(response, {
    "discovery returned one persisted medication": (value) =>
      value.status === 200 && value.json("items.0.id") !== undefined,
  });
  if (!valid) {
    throw new Error("A populated RxRelay API is required for this benchmark");
  }
  return { drugId: response.json("items.0.id") };
}

function verifiedGet(path, endpoint) {
  const response = http.get(`${BASE_URL}${path}`, { tags: { endpoint } });
  check(response, { [`${endpoint} returned HTTP 200`]: (value) => value.status === 200 });
}

export default function (data) {
  verifiedGet("/api/v1/drugs?query=a&size=20&sort=name,asc", "medication-search");
  verifiedGet(`/api/v1/drugs/${data.drugId}`, "drug-detail");
  verifiedGet(`/api/v1/drugs/${data.drugId}/timeline?size=20`, "timeline");
  verifiedGet("/api/v1/watchlists?size=20", "watchlists");
  sleep(0.05);
}
