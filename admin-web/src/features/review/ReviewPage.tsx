import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { CheckCircle2, Image, ShieldCheck } from 'lucide-react';
import { useState } from 'react';
import { services, ReviewBook } from '../../lib/services';

function assetReady(asset: ReviewBook['assets'][number]) {
  return asset.status === 'ACTIVE' && asset.versionStatus === 'AVAILABLE' && asset.availabilityStatus === 'AVAILABLE';
}

export function ReviewPage() {
  const qc = useQueryClient();
  const [page, setPage] = useState(0);
  const books = useQuery({ queryKey: ['review-books', page], queryFn: () => services.listReviewBooks(page) });
  const action = useMutation({
    mutationFn: ({ kind, bookId, assetId }: { kind: 'book' | 'asset'; bookId: string; assetId?: string }) => kind === 'book' ? services.approveBook(bookId) : services.approveAsset(bookId, assetId!),
    onSuccess: () => void qc.invalidateQueries({ queryKey: ['review-books'] }),
  });
  if (books.isLoading) return <section className="panel"><h1>Review Queue</h1><p>Carregando livros e assets aguardando revisão…</p></section>;
  if (books.isError) return <section className="panel"><h1>Fila indisponível</h1><p>{books.error instanceof Error ? books.error.message : 'Não foi possível carregar a fila de revisão.'}</p></section>;
  const items = books.data?.items ?? [];
  return <section>
    <header className="pagehead"><div><span className="eyebrow">CONTENT GOVERNANCE</span><h1>Review Queue</h1><p>Aprove o livro e cada thumbnail separadamente. Um thumbnail só aparece na aplicação quando o asset, a versão física e os direitos estão aprovados.</p></div><span className="sessionbadge">{books.data?.totalItems ?? 0} aguardando</span></header>
    {action.isError && <div className="error-box" role="alert">{action.error instanceof Error ? action.error.message : 'A operação foi rejeitada.'}</div>}
    {items.length === 0 ? <div className="panel"><h2>Nenhum item aguardando revisão</h2><p>Novas importações com status REVIEW_REQUIRED aparecerão aqui.</p></div> : <div className="review-list">{items.map(book => <article className="review-card" key={book.id}>
      <div className="review-card-head"><div><span className="source">GUTENBERG / REVIEW_REQUIRED</span><h2>{book.canonicalTitle ?? book.originalTitle ?? 'Obra sem título'}</h2><p>{book.originalTitle && book.originalTitle !== book.canonicalTitle ? book.originalTitle : ''} {book.originalLanguage ? `· ${book.originalLanguage.toUpperCase()}` : ''} {book.firstPublicationYear ? `· ${book.firstPublicationYear}` : ''}</p></div><button className="primary-action" disabled={action.isPending} onClick={() => action.mutate({ kind:'book', bookId:book.id })}><ShieldCheck size={16}/>Aprovar livro</button></div>
      {book.description && <p className="review-description">{book.description}</p>}
      <div className="review-assets"><strong>Assets associados</strong>{book.assets.length === 0 ? <p>Nenhum asset encontrado.</p> : book.assets.map(asset => <div className="review-asset" key={asset.id}><div><Image size={17}/><span><b>{asset.assetType} · {asset.assetRole}</b><small>{asset.contentType ?? 'tipo desconhecido'} · {asset.sizeBytes ? `${Math.round(asset.sizeBytes / 1024)} KB` : 'tamanho desconhecido'} · {asset.versionStatus ?? 'sem versão'}</small></span></div>{assetReady(asset) ? <span className="review-approved"><CheckCircle2 size={16}/>Aprovado</span> : <button className="secondary-action" disabled={action.isPending} onClick={() => action.mutate({ kind:'asset', bookId:book.id, assetId:asset.id })}><CheckCircle2 size={16}/>Aprovar asset</button>}</div>)}</div>
    </article>)}</div>}
    <div className="toolbar"><button disabled={page === 0} onClick={() => setPage(current => current - 1)}>Página anterior</button><button disabled={books.data?.last ?? true} onClick={() => setPage(current => current + 1)}>Próxima página</button></div>
  </section>;
}
