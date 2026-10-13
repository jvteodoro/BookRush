import { useEffect, useRef, useState } from 'react';
import { AppShell, type ReaderTab } from '../components/layout/AppShell';
import { BookRow } from '../components/book/BookRow';
import type { ReaderBook } from '../components/book/BookCard';
import { Button } from '../components/ui/Button';
import { Card } from '../components/ui/Card';
import { Chip } from '../components/ui/Chip';
import { ExcerptFeed } from '../components/book/ExcerptFeed';
import { Switch } from '../components/ui/Switch';

const books: ReaderBook[] = [
  { id: 'dom-casmurro', title: 'Dom Casmurro', author: 'Machado de Assis', genre: 'Romance', progress: 38, minutes: 15, description: 'Memória, afeto e dúvida se encontram na história de Bentinho e Capitu.' },
  { id: 'sherlock', title: 'Um estudo em vermelho', author: 'Arthur Conan Doyle', genre: 'Suspense', progress: 12, minutes: 15, description: 'O primeiro encontro de Sherlock Holmes e Dr. Watson inaugura um mistério em Londres.' },
  { id: 'alice', title: 'Alice no País das Maravilhas', author: 'Lewis Carroll', genre: 'Fantasia', progress: 0, minutes: 10, description: 'Uma viagem por um mundo onde a curiosidade transforma tudo.' },
  { id: 'ilha', title: 'A ilha do tesouro', author: 'Robert Louis Stevenson', genre: 'Aventura', progress: 0, minutes: 15, description: 'Um mapa, um navio e uma aventura em busca de um tesouro escondido.' },
  { id: 'orgulho', title: 'Orgulho e preconceito', author: 'Jane Austen', genre: 'Romance', progress: 0, minutes: 12, description: 'Elizabeth Bennet desafia as primeiras impressões e as expectativas de sua época.' },
  { id: 'frankenstein', title: 'Frankenstein', author: 'Mary Shelley', genre: 'Suspense', progress: 0, minutes: 14, description: 'Uma criação extraordinária levanta perguntas sobre responsabilidade e humanidade.' },
  { id: 'vinte-mil-leguas', title: 'Vinte mil léguas submarinas', author: 'Júlio Verne', genre: 'Aventura', progress: 24, minutes: 15, description: 'Uma expedição pelo oceano revela os segredos do capitão Nemo.' },
  { id: 'jardim-secreto', title: 'O jardim secreto', author: 'Frances Hodgson Burnett', genre: 'Fantasia', progress: 0, minutes: 12, description: 'Um jardim esquecido ganha vida através da amizade e da curiosidade.' },
  { id: 'memorias-postumas', title: 'Memórias póstumas de Brás Cubas', author: 'Machado de Assis', genre: 'Romance', progress: 61, minutes: 10, description: 'Um narrador inesperado revisita a própria vida com humor e ironia.' },
  { id: 'dracula', title: 'Drácula', author: 'Bram Stoker', genre: 'Suspense', progress: 0, minutes: 14, description: 'Cartas e diários revelam um mistério que atravessa fronteiras.' },
  { id: 'volta-ao-mundo', title: 'A volta ao mundo em oitenta dias', author: 'Júlio Verne', genre: 'Aventura', progress: 0, minutes: 15, description: 'Uma aposta transforma cada etapa da viagem em uma corrida contra o tempo.' },
  { id: 'magico-oz', title: 'O mágico de Oz', author: 'L. Frank Baum', genre: 'Fantasia', progress: 0, minutes: 10, description: 'Uma estrada de tijolos amarelos une companheiros em busca de novos caminhos.' },
];

export function HomePreview() {
  const [tab, setTab] = useState<ReaderTab>('feed');
  const [query, setQuery] = useState('');
  const [genre, setGenre] = useState('Todos');
  const [selected, setSelected] = useState<ReaderBook | null>(null);
  const [saved, setSaved] = useState(() => books.map(book => book.id));
  const [privacy, setPrivacy] = useState({ activity: true, library: true, streak: true, followers: true });
  const detail = useRef<HTMLDivElement>(null);
  useEffect(() => { if (selected) detail.current?.focus(); }, [selected]);
  const navigate = (next: ReaderTab) => { setSelected(null); setTab(next); };
  const toggleSaved = (id: string) => setSaved(current => current.includes(id) ? current.filter(item => item !== id) : [...current, id]);
  const filtered = books.filter(book => genre === 'Todos' || book.genre === genre);
  const results = books.filter(book => `${book.title} ${book.author}`.toLocaleLowerCase('pt-BR').includes(query.toLocaleLowerCase('pt-BR')));
  return <AppShell active={tab} onNavigate={navigate} query={query} onSearch={setQuery}>
    <div className="br-demo-notice">Prévia local · livros e perfil demonstrativos <a href="/design-system">Ver componentes →</a></div>
    {selected ? <Card className="br-book-detail" ref={detail} tabIndex={-1}>
      <Button variant="ghost" size="sm" onClick={() => setSelected(null)}>← Voltar</Button><h1>{selected.title}</h1><p>{selected.author} · {selected.genre}</p><p className="br-reading-sample">{selected.description}</p>
      <Button variant="secondary" onClick={() => toggleSaved(selected.id)}>{saved.includes(selected.id) ? 'Remover da biblioteca' : 'Salvar na biblioteca'}</Button>
      <p>Esta prévia demonstra os detalhes e a biblioteca. A abertura do leitor será conectada à API na integração.</p>
    </Card> : <>
      {tab === 'feed' && <ExcerptFeed books={books} saved={saved} onSave={toggleSaved} onOpen={setSelected} />}
      {tab === 'recommendations' && <>
        <div className="br-home-heading"><span className="br-kicker">SEU MOMENTO DE LEITURA</span><h1>Boa noite, Ana</h1><p>Uma boa história está esperando por você.</p></div>
        <div className="br-home-resume"><Card className="br-streak"><strong>12</strong><div><b>DIAS SEGUIDOS LENDO</b><p>Faltam 6 min para a meta de hoje</p></div></Card><Button size="lg" onClick={() => setSelected(books[0])}>Continuar Dom Casmurro →</Button></div>
        <div className="br-genres" aria-label="Filtrar por gênero">{['Todos', 'Romance', 'Suspense', 'Fantasia', 'Aventura'].map(item => <Chip key={item} active={genre === item} onClick={() => setGenre(item)}>{item}</Chip>)}</div>
        <BookRow title="Continue de onde parou" books={filtered.filter(book => book.progress > 0)} onOpen={setSelected} />
        <BookRow title="Leituras de 15 minutos" books={filtered.filter(book => book.progress === 0)} onOpen={setSelected} />
      </>}
      {tab === 'search' && <><h1>Encontre sua próxima história</h1><label className="br-search-label" htmlFor="page-search">Título ou autor</label><input className="br-search-input" id="page-search" placeholder="Digite para buscar…" value={query} onChange={event => setQuery(event.target.value)} /><BookRow title={query ? `Resultados (${results.length})` : 'Explore os livros'} books={results} onOpen={setSelected} /></>}
      {tab === 'library' && <><h1>Sua biblioteca</h1><p className="br-muted">Livros salvos nesta prévia. As mudanças duram até recarregar a página.</p><BookRow title="Na sua estante" books={books.filter(book => saved.includes(book.id))} onOpen={setSelected} /></>}
      {tab === 'profile' && <><h1>Seu perfil</h1><Card className="br-profile-card"><span className="br-avatar" aria-hidden="true">A</span><h2>Ana</h2><p>Entre um capítulo e outro, descobrindo novos mundos.</p><p>12 dias de leitura · {saved.length} livros na biblioteca</p><p className="br-muted">Perfil demonstrativo</p></Card></>}
      {tab === 'plant' && <><h1>Minha planta</h1><Card className="br-profile-card"><div className="br-plant-art" aria-hidden="true">🌱</div><h2>Seu hábito está crescendo</h2><p>12 dias de sequência · 4 minutos lidos hoje</p><p>A cada leitura, você cuida um pouco mais da sua planta.</p><Button onClick={() => navigate('feed')}>Encontrar uma leitura</Button></Card></>}
      {tab === 'privacy' && <><h1>Privacidade</h1><Card className="br-profile-card"><h2>Você escolhe o que compartilhar</h2>{([['activity', 'Atividade de leitura'], ['library', 'Biblioteca'], ['streak', 'Sequência de leitura'], ['followers', 'Seguidores']] as const).map(([key, label]) => <Switch className="br-privacy-option" key={key} label={label} checked={privacy[key]} onChange={event => setPrivacy(current => ({ ...current, [key]: event.target.checked }))} />)}<p>Preferências demonstrativas, mantidas somente até recarregar a página.</p></Card></>}
      {(tab === 'admin' || tab === 'publisher') && <><h1>{tab === 'admin' ? 'Administração' : 'Publicador'}</h1><Card className="br-profile-card"><h2>{tab === 'admin' ? 'Visão geral da plataforma' : 'Suas publicações'}</h2><p>{tab === 'admin' ? 'Catálogo, ingestão e acompanhamento dos serviços.' : 'Publicação de livros e acompanhamento de conteúdo.'}</p><p>Esta área mantém seu lugar na navegação. As ferramentas e permissões reais serão conectadas na integração.</p></Card></>}
      {tab === 'prototype' && <><h1>Menu do protótipo</h1><Card className="br-profile-card"><p>Explore a nova identidade visual do BookRush.</p><div className="br-preview__row"><Button onClick={() => navigate('feed')}>Para você</Button><a href="/design-system">Componentes do design system →</a></div></Card></>}
    </>}
  </AppShell>;
}
