# ADR-006 — Matching determinístico e revisão humana

Matching compara obra, pessoa e edição separadamente. A normalização afeta
somente comparação (Unicode/case/espaços/pontuação); valores exibidos são
preservados. Candidatos são bloqueados localmente e limitados a 20 com empate
ordenado por identificador estável.

A pontuação é `.55 título + .35 contribuidor + .10 idioma`; campo ausente vale
zero e não é reponderado. `AUTO_ACCEPTED` exige score >= .95, margem >= .10,
evidência suficiente e nenhuma contradição. Score >= .80 vai para
`REVIEW_REQUIRED`; o restante é `NO_MATCH`. ISBN e presença de catálogo não são
prova de equivalência editorial. Decisões humanas persistem ator, motivo,
evidências e versão esperada.
