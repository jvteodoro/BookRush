export function SeenBadge({ seen }: { seen?: boolean }) {
  return seen ? <span className="br-seen-badge" title="O conteúdo deste livro já foi aberto no leitor.">✓ Visto</span> : null;
}
