export function statusText(status: string) {
  if (status === "UNKNOWN_FRESHNESS") return "UNKNOWN_FRESHNESS — atualização não informada";
  if (status === "UNTRUSTED") return "UNTRUSTED — estado legado";
  if (status === "NO_DATA") return "NO_DATA — não há snapshot no período";
  if (status === "ERROR") return "ERROR — fonte indisponível";
  return status;
}
