# Fontes e snapshots

Gutenberg é consumido por catálogo RDF/XML oficial e Open Library por dumps
TSV+JSON congelados em snapshot. O parser Open Library separa exatamente os
primeiros quatro delimitadores TAB e trata o quinto campo como JSON; autores,
obras e edições permanecem namespaces distintos. Redirects e deletes são
observações do snapshot e não apagam automaticamente histórico compartilhado.

O serviço não consulta a API Open Library por livro, não raspa páginas humanas e
não infere URLs a partir de filenames. URLs de formatos são aceitas somente
quando vierem do catálogo machine-readable ou de índice de mirror explicitamente
autorizado. Cada snapshot registra URL resolvida, validators, tamanho, hashes,
parser e filtros usados.
