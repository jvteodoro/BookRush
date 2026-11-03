# Spoiler components

Migration `V27__spoiler_components.sql` adiciona três definições de feature no
escopo `EXCERPT`: `spoiler.position`, `spoiler.resolution` e
`spoiler.revelation`. Os valores são componentes explicáveis derivados da
posição relativa e dos scores NLI versionados; a ausência de qualquer entrada
NLI impede a persistência dos componentes, evitando converter dado ausente em
zero.

O contrato é `spoiler-components-v1`. Não existe `spoiler_probability`: uma
probabilidade calibrada exige dataset anotado, avaliação e promoção explícita.
As observações continuam lineageadas ao `analysis_run` e são idempotentes.
