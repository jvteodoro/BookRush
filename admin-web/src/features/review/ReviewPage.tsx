import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import type { UseMutationResult } from '@tanstack/react-query';
import { CheckCircle2, ExternalLink, Image, RotateCcw, ShieldCheck } from 'lucide-react';
import { useState } from 'react';
import { services, ReviewBook } from '../../lib/services';

type ReviewActionInput = { kind: 'book' | 'asset'; bookId: string; assetId?: string };
type PreviewInput = { bookId: string; assetId: string; popup?: Window };
type ReviewAction = Pick<UseMutationResult<void, Error, ReviewActionInput>, 'isPending' | 'mutate'>;
type PreviewAction = Pick<UseMutationResult<unknown, Error, PreviewInput>, 'isPending'>;
type RevokeAction = Pick<UseMutationResult<void, Error, string>, 'isPending' | 'mutate'>;

function assetReady(asset: ReviewBook['assets'][number]) {
  return asset.status === 'ACTIVE' && asset.versionStatus === 'AVAILABLE' && asset.availabilityStatus === 'AVAILABLE';
}

function formatDate(value?: string) {
  if (!value) return 'data desconhecida';
  return new Intl.DateTimeFormat('pt-BR', { dateStyle: 'short', timeStyle: 'short' }).format(new Date(value));
}

export function ReviewPage() {
  const qc = useQueryClient();
  const [tab, setTab] = useState<'queue' | 'history'>('queue');
  const [page, setPage] = useState(0);
  const books = useQuery({ queryKey: ['review-books', page], queryFn: () => services.listReviewBooks(page), enabled: tab === 'queue' });
  const history = useQuery({ queryKey: ['approval-history', page], queryFn: () => services.listApprovalHistory(page), enabled: tab === 'history' });
  const action = useMutation({
    mutationFn: ({ kind, bookId, assetId }: { kind: 'book' | 'asset'; bookId: string; assetId?: string }) => kind === 'book' ? services.approveBook(bookId) : services.approveAsset(bookId, assetId!),
    onSuccess: () => { void qc.invalidateQueries({ queryKey: ['review-books'] }); void qc.invalidateQueries({ queryKey: ['approval-history'] }); },
  });
  const revoke = useMutation({
    mutationFn: services.revokeBookApproval,
    onSuccess: () => { void qc.invalidateQueries({ queryKey: ['approval-history'] }); void qc.invalidateQueries({ queryKey: ['review-books'] }); },
  });
  const preview = useMutation({
    mutationFn: ({ bookId, assetId }: { bookId: string; assetId: string; popup?: Window }) => services.getAssetDownloadUrl(bookId, assetId),
    onSuccess: (link, variables) => { if (variables.popup && !variables.popup.closed) variables.popup.location.href = link.url; },
    onError: (_error, variables) => variables.popup?.close(),
  });
  function openAsset(bookId: string, assetId: string) {
    const popup = window.open('about:blank', '_blank', 'noopener,noreferrer');
    if (popup) preview.mutate({ bookId, assetId, popup });
  }
  const error = action.error ?? revoke.error ?? preview.error;
  return <section>
    <header className="pagehead"><div><span className="eyebrow">CONTENT GOVERNANCE</span><h1>Review Queue</h1><p>Inspecione os arquivos antes de aprovar livros e thumbnails. Toda decisão fica registrada e uma aprovação pode ser revogada.</p></div><span className="sessionbadge">{tab === 'queue' ? `${books.data?.totalItems ?? 0} aguardando` : `${history.data?.totalItems ?? 0} decisões`}</span></header>
    <div className="review-tabs" role="tablist" aria-label="Revisão editorial"><button className={tab === 'queue' ? 'active' : ''} onClick={() => { setTab('queue'); setPage(0); }}>Fila de revisão</button><button className={tab === 'history' ? 'active' : ''} onClick={() => { setTab('history'); setPage(0); }}>Histórico de aprovações</button></div>
    {error && <div className="error-box" role="alert">{error instanceof Error ? error.message : 'A operação foi rejeitada.'}</div>}
    {tab === 'queue' ? <QueueContent books={books.data?.items ?? []} loading={books.isLoading} error={books.error} action={action} preview={preview} openAsset={openAsset} /> : <HistoryContent history={history.data?.items ?? []} loading={history.isLoading} error={history.error} revoke={revoke} />}
    <div className="toolbar"><button disabled={page === 0} onClick={() => setPage(current => current - 1)}>Página anterior</button><button disabled={(tab === 'queue' ? books.data?.last : history.data?.last) ?? true} onClick={() => setPage(current => current + 1)}>Próxima página</button></div>
  </section>;
}

function QueueContent({ books, loading, error, action, preview, openAsset }: { books: ReviewBook[]; loading: boolean; error: unknown; action: ReviewAction; preview: PreviewAction; openAsset: (bookId: string, assetId: string) => void }) {
  if (loading) return <section className="panel"><h2>Carregando fila</h2><p>Buscando livros e assets aguardando revisão…</p></section>;
  if (error) return <section className="panel"><h2>Fila indisponível</h2><p>{error instanceof Error ? error.message : 'Não foi possível carregar a fila de revisão.'}</p></section>;
  if (books.length === 0) return <div className="panel"><h2>Nenhum item aguardando revisão</h2><p>Novas importações com status REVIEW_REQUIRED aparecerão aqui.</p></div>;
  return <div className="review-list">{books.map(book => <article className="review-card" key={book.id}>
    <div className="review-card-head"><div><span className="source">GUTENBERG / REVIEW_REQUIRED</span><h2>{book.canonicalTitle ?? book.originalTitle ?? 'Obra sem título'}</h2><p>{book.originalTitle && book.originalTitle !== book.canonicalTitle ? book.originalTitle : ''} {book.originalLanguage ? `· ${book.originalLanguage.toUpperCase()}` : ''} {book.firstPublicationYear ? `· ${book.firstPublicationYear}` : ''}</p></div><button className="primary-action" disabled={action.isPending} onClick={() => action.mutate({ kind: 'book', bookId: book.id })}><ShieldCheck size={16}/>Aprovar livro</button></div>
    {book.description && <p className="review-description">{book.description}</p>}
    <div className="review-assets"><strong>Assets associados</strong>{book.assets.length === 0 ? <p>Nenhum asset encontrado.</p> : book.assets.map(asset => <div className="review-asset" key={asset.id}><div><Image size={17}/><span><b>{asset.assetType} · {asset.assetRole}</b><small>{asset.contentType ?? 'tipo desconhecido'} · {asset.sizeBytes ? `${Math.round(asset.sizeBytes / 1024)} KB` : 'tamanho desconhecido'} · {asset.versionStatus ?? 'sem versão'}</small></span></div><div className="review-asset-actions">{asset.versionId && <button className="secondary-action" disabled={preview.isPending} onClick={() => openAsset(book.id, asset.id)}><ExternalLink size={15}/>Abrir para revisão</button>}{assetReady(asset) ? <span className="review-approved"><CheckCircle2 size={16}/>Aprovado</span> : <button className="secondary-action" disabled={action.isPending} onClick={() => action.mutate({ kind: 'asset', bookId: book.id, assetId: asset.id })}><CheckCircle2 size={16}/>Aprovar asset</button>}</div></div>)}</div>
  </article>)}</div>;
}

function HistoryContent({ history, loading, error, revoke }: { history: Array<{ id: string; bookId: string; canonicalTitle?: string; originalTitle?: string; action: string; previousStatus: string; newStatus: string; actorSubject: string; createdAt: string; currentStatus: string }>; loading: boolean; error: unknown; revoke: RevokeAction }) {
  if (loading) return <section className="panel"><h2>Carregando histórico</h2><p>Buscando decisões editoriais…</p></section>;
  if (error) return <section className="panel"><h2>Histórico indisponível</h2><p>{error instanceof Error ? error.message : 'Não foi possível carregar o histórico.'}</p></section>;
  if (history.length === 0) return <div className="panel"><h2>Nenhuma decisão registrada</h2><p>Aprovações e revogações aparecerão aqui.</p></div>;
  return <div className="review-history">{history.map(item => <article className="history-row" key={item.id}><div><span className={`history-action ${item.action.toLowerCase()}`}>{item.action === 'APPROVED' ? 'Aprovado' : 'Revogado'}</span><h2>{item.canonicalTitle ?? item.originalTitle ?? 'Obra sem título'}</h2><p>{formatDate(item.createdAt)} · {item.actorSubject} · {item.previousStatus} → {item.newStatus}</p></div>{item.action === 'APPROVED' && item.currentStatus === 'ACTIVE' && <button className="secondary-action" disabled={revoke.isPending} onClick={() => revoke.mutate(item.bookId)}><RotateCcw size={15}/>Revogar aprovação</button>}</article>)}</div>;
}
