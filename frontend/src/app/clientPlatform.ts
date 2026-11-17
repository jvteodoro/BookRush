/** Coarse client family only; never persist or forward the user-agent string. */
export function clientPlatform(): "web" | "mobile" {
  if (typeof navigator === "undefined") return "web";
  const mobileHint = (
    navigator as Navigator & { userAgentData?: { mobile?: boolean } }
  ).userAgentData?.mobile;
  if (mobileHint === true) return "mobile";
  return /Android|iPhone|iPad|iPod|Mobile/i.test(navigator.userAgent)
    ? "mobile"
    : "web";
}
