export function statusText(status: string) {
  if (status === "UNTRUSTED")
    return "UNTRUSTED — população ainda não classificada";
  if (status === "NO_DATA") return "NO_DATA — não há snapshot no período";
  if (status === "ERROR") return "ERROR — fonte indisponível";
  return status;
}
