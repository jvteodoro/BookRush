import { useEffect, useRef, useState } from 'react';
import { Drawer } from '../components/ui/Drawer';
import { Button } from '../components/ui/Button';
import { loadComments, postComment, reportComment, type BookComment } from './comments';

type Props = { book: { id: string; title: string }; userName: string; onClose: () => void; onProfile: (subject: string) => void; onCount: (id: string, count: number) => void };
export function CommentsDrawer({ book, userName, onClose, onProfile, onCount }: Props) {
  const [comments, setComments] = useState<BookComment[]>([]);
  const [draft, setDraft] = useState('');
  const [loading, setLoading] = useState(true);
  const [sending, setSending] = useState(false);
  const [error, setError] = useState('');
  const [loadError, setLoadError] = useState('');
  const [notice, setNotice] = useState('');
  const [attempt, setAttempt] = useState(0);
  const [reportingId, setReportingId] = useState('');
  const sendLock = useRef(false);
  useEffect(() => {
    const controller = new AbortController();
    setLoading(true); setLoadError('');
    void loadComments(book.id, controller.signal).then(rows => {
      if (controller.signal.aborted) return;
      setComments(rows); onCount(book.id, rows.length);
    }).catch(error => { if (!controller.signal.aborted) setLoadError(error.message); }).finally(() => { if (!controller.signal.aborted) setLoading(false); });
    return () => controller.abort();
  }, [book.id, attempt, onCount]);
  const submit = async () => {
    if (sendLock.current || !draft.trim()) return;
    sendLock.current = true; setSending(true); setError(''); setNotice('');
    try {
      const posted = await postComment(book.id, draft);
      setDraft('');
      const confirmed = [...comments, { ...posted, authorName: userName }];
      setComments(confirmed); onCount(book.id, confirmed.length);
      setNotice('Comentário publicado.');
      try { const rows = await loadComments(book.id); setComments(rows); onCount(book.id, rows.length); }
      catch { setNotice('Comentário publicado. Não foi possível atualizar a lista agora.'); }
    } catch (error) { setError(error instanceof Error ? error.message : 'Não foi possível publicar o comentário.'); }
    finally { sendLock.current = false; setSending(false); }
  };
  const report = async (comment: BookComment) => {
    const reason = window.prompt('Por que este comentário deve ser revisado? (até 120 caracteres)')?.trim();
    if (!reason) return;
    setReportingId(comment.id); setError(''); setNotice('');
    try { await reportComment(book.id, comment.id, reason); setNotice('Comentário denunciado. A moderação já pode revisá-lo.'); }
    catch (error) { setError(error instanceof Error ? error.message : 'Não foi possível denunciar o comentário.'); }
    finally { setReportingId(''); }
  };
  return <Drawer title="Comentários" subtitle={book.title} onClose={onClose} footer={<form className="br-comment-compose" onSubmit={event => { event.preventDefault(); void submit(); }}>
    <label htmlFor="comment-draft">Seu comentário</label><textarea id="comment-draft" rows={3} maxLength={2000} placeholder="O que você achou desta leitura?" value={draft} disabled={sending} onChange={event => setDraft(event.target.value)} />
    <div className="br-comment-compose__actions"><small>{draft.length}/2000</small><Button size="sm" type="submit" disabled={sending || loading || Boolean(loadError) || !draft.trim()}>{sending ? 'Publicando…' : 'Publicar'}</Button></div>
    {error && <p role="alert" className="br-comment-error">{error}</p>}{notice && <p role="status">{notice}</p>}
  </form>}>
    {loading ? <p role="status">Carregando comentários…</p> : loadError ? <div><p role="alert">{loadError}</p><Button variant="ghost" onClick={() => setAttempt(current => current + 1)}>Tentar novamente</Button></div> : !comments.length ? <div className="br-comments-empty"><h3>A conversa começa aqui</h3><p>Seja a primeira pessoa a comentar esta leitura.</p></div> : <ol className="br-comments-list">{comments.map(comment => {
      const name = comment.authorName?.trim() || 'Leitor';
      const author = <><span className="br-comment-avatar" aria-hidden="true">{name.slice(0, 2).toUpperCase()}</span><strong>{name}</strong></>;
      return <li className="br-comment" key={comment.id}><div className="br-comment-heading">{comment.subject_key ? <a className="br-comment-author" href={`/usuarios/${encodeURIComponent(comment.subject_key)}`} onClick={event => { event.preventDefault(); onProfile(comment.subject_key!); }}>{author}</a> : <div className="br-comment-author">{author}</div>}<button type="button" className="br-comment-report" disabled={Boolean(reportingId)} onClick={() => void report(comment)}>{reportingId === comment.id ? 'Enviando…' : 'Denunciar'}</button></div><p>{comment.body}</p></li>;
    })}</ol>}
  </Drawer>;
}
