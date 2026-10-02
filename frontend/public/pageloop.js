(() => {
  const mode = document.body.dataset.mode || 'web';
  const initialRoute = document.body.dataset.start || 'feed';

  const BOOKS = window.__BOOKRUSH_BOOKS__ || [];

  const defaultState = {
    route: initialRoute,
    likes:{...(window.__BOOKRUSH_LIKES__||{})}, saved:Object.fromEntries(Array.from(window.__BOOKRUSH_API__?.saved || []).map(id=>[id,true])), followed:{}, blocked:{}, removedReports:{},
    readerBook:BOOKS[0]?.id||'', readerPage: 0, readerSize:20, readerNight:false,
    libraryTab:'saved', search:'', searchFilter:'Todos', streak:0, minutes:0, booksMonth:0,
    privacy:{activity:true,library:true,streak:true,followers:true}, recent:window.__BOOKRUSH_RECENT__||[], adminTab:'overview', publisherTab:'publish', otherUser:'',
    published:[{id:'p1',title:'Cidades de Vidro',author:'Editora Horizonte',views:128400,reads:34200,likes:18400,shares:3910,a:'#7e91ff',b:'#291c54'}]
  };

  const persisted = {}; // estado de interface vive em memória; dados do produto ficam nas APIs
  const state = Object.assign({}, defaultState, persisted, {route: initialRoute});
  state.privacy = Object.assign({}, defaultState.privacy, persisted.privacy || {});

  const ICONS = {
    home:'<path d="M3 10.5 12 3l9 7.5"/><path d="M5 9.5V21h14V9.5"/><path d="M9 21v-7h6v7"/>',
    sparkle:'<path d="M12 3l1.6 4.2L18 9l-4.4 1.8L12 15l-1.6-4.2L6 9l4.4-1.8L12 3Z"/><path d="M18.5 15.5 19.3 18l2.2.8-2.2.8-.8 2.4-.8-2.4-2.2-.8 2.2-.8.8-2.5Z"/>',
    search:'<circle cx="11" cy="11" r="7"/><path d="m20 20-4-4"/>',
    library:'<path d="M4 5h5v14H4zM10 5h5v14h-5zM16 6l4-1 2 13-4 1z"/>',
    leaf:'<path d="M19 4C11 4 5 8.5 5 14c0 3 2.2 5 5 5 5.5 0 9-6 9-15Z"/><path d="M5 21c2-6 6-9 11-12"/>',
    user:'<circle cx="12" cy="8" r="4"/><path d="M4 21c.7-4.2 3.2-6 8-6s7.3 1.8 8 6"/>',
    settings:'<circle cx="12" cy="12" r="3"/><path d="M19.4 15a1.7 1.7 0 0 0 .3 1.9l.1.1-2.8 2.8-.1-.1a1.7 1.7 0 0 0-1.9-.3 1.7 1.7 0 0 0-1 1.6v.2h-4V21a1.7 1.7 0 0 0-1-1.6 1.7 1.7 0 0 0-1.9.3l-.1.1L4.2 17l.1-.1a1.7 1.7 0 0 0 .3-1.9A1.7 1.7 0 0 0 3 14H2.8v-4H3a1.7 1.7 0 0 0 1.6-1 1.7 1.7 0 0 0-.3-1.9L4.2 7 7 4.2l.1.1a1.7 1.7 0 0 0 1.9.3A1.7 1.7 0 0 0 10 3V2.8h4V3a1.7 1.7 0 0 0 1 1.6 1.7 1.7 0 0 0 1.9-.3l.1-.1L19.8 7l-.1.1a1.7 1.7 0 0 0-.3 1.9 1.7 1.7 0 0 0 1.6 1h.2v4H21a1.7 1.7 0 0 0-1.6 1Z"/>',
    heart:'<path d="M20.8 4.6a5.5 5.5 0 0 0-7.8 0L12 5.7l-1.1-1.1a5.5 5.5 0 0 0-7.8 7.8l1.1 1.1L12 21l7.8-7.5 1.1-1.1a5.5 5.5 0 0 0-.1-7.8Z"/>',
    comment:'<path d="M21 15a4 4 0 0 1-4 4H8l-5 3V7a4 4 0 0 1 4-4h10a4 4 0 0 1 4 4Z"/>',
    share:'<circle cx="18" cy="5" r="3"/><circle cx="6" cy="12" r="3"/><circle cx="18" cy="19" r="3"/><path d="m8.6 10.6 6.8-4.2M8.6 13.4l6.8 4.2"/>',
    bookmark:'<path d="M6 3h12v18l-6-4-6 4Z"/>',
    play:'<path d="m9 7 8 5-8 5Z"/>',
    clock:'<circle cx="12" cy="12" r="9"/><path d="M12 7v5l3 2"/>',
    shield:'<path d="M12 3 4 6v5c0 5 3.5 8.5 8 10 4.5-1.5 8-5 8-10V6l-8-3Z"/>',
    chart:'<path d="M4 20V10M10 20V4M16 20v-7M22 20V7"/>',
    upload:'<path d="M12 16V4M7 9l5-5 5 5"/><path d="M4 14v6h16v-6"/>',
    admin:'<rect x="3" y="4" width="18" height="16" rx="2"/><path d="M8 9h8M8 13h5"/>',
    chevron:'<path d="m9 18 6-6-6-6"/>',
    close:'<path d="M5 5l14 14M19 5 5 19"/>',
    sun:'<circle cx="12" cy="12" r="4"/><path d="M12 2v2M12 20v2M4.9 4.9l1.4 1.4M17.7 17.7l1.4 1.4M2 12h2M20 12h2M4.9 19.1l1.4-1.4M17.7 6.3l1.4-1.4"/>',
    type:'<path d="M4 6V4h16v2M12 4v16M8 20h8"/>',
    arrow:'<path d="m5 12 14 0M13 6l6 6-6 6"/>',
    menu:'<path d="M4 7h16M4 12h16M4 17h16"/>',
    bell:'<path d="M18 8a6 6 0 0 0-12 0c0 7-3 7-3 9h18c0-2-3-2-3-9M10 21h4"/>'
  };

  function icon(name, cls='') { return `<svg class="icon ${cls}" viewBox="0 0 24 24" aria-hidden="true">${ICONS[name] || ICONS.sparkle}</svg>`; }
  function esc(s=''){return String(s).replace(/[&<>'"]/g,m=>({'&':'&amp;','<':'&lt;','>':'&gt;',"'":'&#39;','"':'&quot;'}[m]));}
  function compact(n){return n>=1000?(n/1000).toFixed(n>=10000?0:1).replace('.',',')+' mil':String(n)}
  function book(id){return BOOKS.find(b=>b.id===id)||BOOKS[0]}
  function save(){const copy={...state};delete copy.route;void copy; /* persistência de produto é feita pelo backend */}
  function toast(msg){const stack=document.querySelector('.toast-stack')||document.body.appendChild(Object.assign(document.createElement('div'),{className:'toast-stack'}));const t=document.createElement('div');t.className='toast';t.textContent=msg;stack.appendChild(t);setTimeout(()=>t.remove(),2300)}

  const NAV = [
    ['feed','home','Para você'],['recommendations','sparkle','Recomendações'],['search','search','Buscar'],['library','library','Biblioteca'],['plant','leaf','Minha planta'],['profile','user','Perfil'],['privacy','settings','Privacidade']
  ];

  function shell(content,title='BookRush'){
    const app=document.getElementById('app');
    app.innerHTML=`<div class="app-shell">
      <aside class="sidebar">
        <div class="logo"><span class="brand-badge">B</span><span class="logo-text">BookRush</span></div>
        <nav class="nav-list">${NAV.map(([r,i,l])=>`<button class="nav-item ${state.route===r?'active':''}" data-route="${r}"><span class="nav-icon-wrap">${icon(i)}</span><span class="nav-label">${l}</span></button>`).join('')}
          <button class="nav-item admin-link ${state.route==='admin'?'active':''}" data-route="admin">${icon('admin')}<span class="nav-label">Administração</span></button>
          <button class="nav-item publisher-link ${state.route==='publisher'?'active':''}" data-route="publisher">${icon('upload')}<span class="nav-label">Publicador</span></button>
        </nav>
        <div class="sidebar-spacer"></div>
        <a href="index.html" class="nav-item">${icon('menu')}<span class="nav-label">Menu do protótipo</span></a>
        <div class="profile-mini"><div class="avatar">JV</div><div><strong>João Victor</strong><span>@joaovictor</span></div></div>
      </aside>
      <section class="main-area">
        <header class="topbar"><div class="topbar-title">${title}</div><div class="topbar-actions"><button class="icon-btn" data-action="notify" aria-label="Notificações">${icon('bell')}</button><button class="icon-btn" data-route="profile" aria-label="Perfil">${icon('user')}</button></div></header>
        ${content}
        ${mobileNav()}
      </section>
      <div class="toast-stack"></div>
    </div>
    ${mode==='web'&&state.route!=='reader'?`<div class="prototype-switcher"><a class="prototype-pill" href="${state.route==='admin'?'admin.html':state.route==='publisher'?'publisher.html':'app.html'}">${state.route==='admin'||state.route==='publisher'?'BookRush':'BookRush Mobile'}</a></div>`:''}`;
    bindCommon();
  }

  function mobileNav(){
    const items=[['feed','home','Início'],['recommendations','sparkle','Descobrir'],['search','search','Buscar'],['library','library','Biblioteca'],['profile','user','Perfil']];
    return `<nav class="mobile-nav">${items.map(([r,i,l])=>`<button data-route="${r}" class="${state.route===r?'active':''}">${icon(i)}<span>${l}</span></button>`).join('')}</nav>`;
  }

  function bindCommon(){
    document.querySelectorAll('[data-route]').forEach(el=>el.addEventListener('click',()=>go(el.dataset.route)));
    document.querySelectorAll('[data-action="notify"]').forEach(el=>el.addEventListener('click',()=>toast('Você está em dia. Nenhuma notificação nova.')));
  }
  function go(route){if(state.route==='reader'&&route!=='reader'){state.route=route;void Promise.resolve(window.__BOOKRUSH_READIUM__?.unmount?.()).catch(()=>{}).finally(render);return;}state.route=route;render();}

  function render(){
    switch(state.route){
      case 'feed': return renderFeed();
      case 'recommendations': return renderRecommendations();
      case 'search': return renderSearch();
      case 'reader': return renderReader();
      case 'library': return renderLibrary();
      case 'profile': return renderProfile();
      case 'otherProfile': return renderOtherProfile();
      case 'privacy': return renderPrivacy();
      case 'plant': return renderPlant();
      case 'admin': return renderAdmin();
      case 'publisher': return renderPublisher();
      default: return renderFeed();
    }
  }

  function feedCard(b,idx){
    return `<article class="feed-card" style="--cover-a:${b.a};--cover-b:${b.b}" data-book="${b.id}">
      <div class="feed-noise"></div>
      <div class="feed-copy">
        <div class="feed-meta"><span class="genre-chip">${b.genre}</span><span class="match">${b.rank ? `posição ${b.rank}` : 'catálogo'}</span><span>•</span><span>${b.modelVersion ? esc(b.modelVersion) : 'conteúdo disponível'}</span></div>
        <div class="quote-mark">“</div><div class="feed-quote">${esc(b.quote)}</div>
        <div class="book-line"><div class="mini-cover"></div><div><strong>${esc(b.title)}</strong><span>${esc(b.author)}</span></div></div>
        <button class="primary-btn open-read" data-open-book="${b.id}">${icon('play','icon-sm')} Ler agora</button>
      </div>
      <div class="action-rail">
        <button class="action ${state.likes[b.id]?'liked':''}" data-like="${b.id}"><span class="action-bubble">${icon('heart','icon-lg')}</span><span class="action-count">${compact(b.likes)}</span></button>
        <button class="action" data-comments="${b.id}"><span class="action-bubble">${icon('comment','icon-lg')}</span><span class="action-count">${compact(b.comments)}</span></button>
        <button class="action ${state.saved[b.id]?'saved':''}" data-save="${b.id}"><span class="action-bubble">${icon('bookmark','icon-lg')}</span><span class="action-count">${state.saved[b.id]?'Salvo':'Salvar'}</span></button>
        <button class="action" data-share="${b.id}"><span class="action-bubble">${icon('share','icon-lg')}</span><span class="action-count">${compact(b.shares)}</span></button>
      </div>
    </article>`;
  }

  function renderFeed(){
    const streak=Number((window.__BOOKRUSH_STREAK__||{}).currentDays||0);
    const main=`<div class="feed-layout"><section class="feed-stage"><div class="feed-scroll" id="feedScroll">${BOOKS.slice(0,6).map(feedCard).join('')}</div></section>
      <aside class="feed-side">
        <div class="side-card"><div class="streak-row"><div class="streak-flame">🔥</div><div><h3>${streak} dias de sequência</h3><p>Dados persistidos pelo reader-state-service.</p></div></div></div>
        <div class="side-card"><h3>Seu catálogo</h3><p>Este feed é composto pelo recommendation-service e registra request, impressão, modelo e posição para cada item.</p><div class="taste-row"><span class="taste">Dados reais</span><span class="taste">heuristic-v1</span></div></div>
        <div class="side-card"><h3>Leitura</h3><p>Abra um livro para registrar a abertura e continue pelo conteúdo reader-ready quando estiver disponível.</p></div>
      </aside></div>`;
    shell(main,'Para você'); bindFeed();
  }

  function bindFeed(){
    document.querySelectorAll('[data-like]').forEach(b=>b.onclick=async()=>{state.likes[b.dataset.like]=!state.likes[b.dataset.like];try{await window.__BOOKRUSH_API__?.like(b.dataset.like,state.likes[b.dataset.like]);await window.__BOOKRUSH_API__?.refreshCounts?.(b.dataset.like);save();renderFeed();}catch(e){state.likes[b.dataset.like]=!state.likes[b.dataset.like];toast(e.message||'Não foi possível curtir');}});
    document.querySelectorAll('[data-save]').forEach(b=>b.onclick=async()=>{state.saved[b.dataset.save]=!state.saved[b.dataset.save];try{await window.__BOOKRUSH_API__?.save(b.dataset.save,state.saved[b.dataset.save]);save();toast(state.saved[b.dataset.save]?'Adicionado à sua biblioteca':'Removido da biblioteca');renderFeed();}catch(e){state.saved[b.dataset.save]=!state.saved[b.dataset.save];toast(e.message||'Não foi possível atualizar a biblioteca');}});
    document.querySelectorAll('[data-comments]').forEach(b=>b.onclick=()=>openComments(b.dataset.comments));
    document.querySelectorAll('[data-share]').forEach(b=>b.onclick=()=>openShare(b.dataset.share));
    document.querySelectorAll('[data-open-book]').forEach(b=>b.onclick=()=>openBook(b.dataset.openBook));
    document.querySelectorAll('[data-follow]').forEach(b=>b.onclick=async()=>{const id=b.dataset.follow, enabled=!state.followed[id];try{await window.__BOOKRUSH_API__?.follow?.(id,enabled);state.followed[id]=enabled;renderFeed();}catch(e){toast(e.message||'Não foi possível atualizar o acompanhamento')}});
    document.querySelectorAll('[data-user-profile]').forEach(b=>b.onclick=()=>{state.otherUser=b.dataset.userProfile;state.route='otherProfile';render();});
    const fs=document.getElementById('feedScroll'); if(fs){document.onkeydown=e=>{if(!['ArrowDown','ArrowUp'].includes(e.key))return;fs.scrollBy({top:(e.key==='ArrowDown'?1:-1)*fs.clientHeight,behavior:'smooth'})}}
  }

  function bookCard(b){return `<article class="book-card" style="--cover-a:${b.a};--cover-b:${b.b}" data-open-book="${b.id}"><div class="book-cover"><span class="cover-mark">BOOKRUSH</span><span class="cover-title">${esc(b.title)}</span></div><h3>${esc(b.title)}</h3><p>${esc(b.author)}</p><div class="book-card-meta"><span>${b.genre}</span><span>${b.rank?`posição ${b.rank}`:'disponível'}</span></div></article>`}

  function renderRecommendations(){
    const content=`<main class="content"><div class="page-heading"><div><div class="eyebrow">CURADORIA PERSONALIZADA</div><h1>Descubra sua próxima leitura</h1><p>Recomendações retornadas pelo recommendation-service e ordenadas pelo modelo ativo.</p></div><button class="primary-btn" id="luckyBtn">${icon('sparkle')} Estou com sorte</button></div>
      <section class="hero-strip"><div><div class="eyebrow">SEU PERFIL DE LEITURA</div><h2>Descobertas baseadas no seu histórico real.</h2><p>As recomendações são retornadas pelo serviço e carregam a versão do modelo e a posição no ranking.</p><div class="hero-actions"><span class="pill">Catálogo</span><span class="pill">Histórico</span><span class="pill">Modelo ativo</span></div></div><div class="taste-orb"><div><div><strong>${BOOKS.length}</strong><span>itens disponíveis</span></div></div></div></section>
      <div class="section-row"><h2>Escolhidos para você</h2><button class="ghost-btn">Ajustar interesses</button></div><div class="book-grid">${BOOKS.slice(0,5).map(bookCard).join('')}</div>
      <div class="section-row"><h2>Mais recomendações do seu feed</h2><span class="muted tiny">Baseado em temas e comportamento</span></div><div class="book-grid">${BOOKS.slice(5,10).map(bookCard).join('')}</div></main>`;
    shell(content,'Recomendações');
    document.querySelectorAll('[data-open-book]').forEach(b=>b.onclick=()=>openBook(b.dataset.openBook));
    document.getElementById('luckyBtn').onclick=()=>{const b=BOOKS[Math.floor(Math.random()*BOOKS.length)];openLucky(b)};
  }

  function openLucky(b){
    modal(`<div class="eyebrow">ESTOU COM SORTE</div><h2>${esc(b.title)}</h2><p>${esc(b.quote)}</p><div style="display:flex;gap:10px;align-items:center;margin-top:18px"><div class="result-cover" style="--cover-a:${b.a};--cover-b:${b.b};background:linear-gradient(145deg,${b.a},${b.b})"></div><div><strong>${esc(b.author)}</strong><div class="muted tiny">${b.genre} • ${b.pages} páginas</div></div></div>`,`<button class="secondary-btn" data-close-modal>Outra vez</button><button class="primary-btn" data-modal-open="${b.id}">Ler este livro ${icon('arrow','icon-sm')}</button>`);
    document.querySelector('[data-close-modal]').onclick=()=>{document.querySelector('.modal-wrap').remove();document.getElementById('luckyBtn').click()};
    document.querySelector('[data-modal-open]').onclick=()=>openBook(b.id);
  }

  function renderSearch(){
    const filters=['Todos','Título','Autor','Palavra-chave'];
    const q=state.search.trim().toLowerCase();
    let results=BOOKS.filter(b=>{
      if(!q)return true; const title=b.title.toLowerCase().includes(q), author=b.author.toLowerCase().includes(q), kw=b.keywords.some(k=>k.includes(q));
      return state.searchFilter==='Título'?title:state.searchFilter==='Autor'?author:state.searchFilter==='Palavra-chave'?kw:(title||author||kw);
    });
    const content=`<main class="content search-wrap"><div class="page-heading"><div><div class="eyebrow">BUSCA UNIVERSAL</div><h1>Encontre qualquer livro</h1><p>Pesquise por título, autor ou palavras-chave.</p></div></div>
      <div class="search-box">${icon('search')}<input id="searchInput" value="${esc(state.search)}" placeholder="Ex.: Jane Austen, futuro, natureza..." autofocus/><kbd>⌘ K</kbd></div>
      <div class="filter-row">${filters.map(f=>`<button class="filter-chip ${state.searchFilter===f?'active':''}" data-filter="${f}">${f}</button>`).join('')}</div>
      <div class="section-row"><h2>${results.length} ${results.length===1?'resultado':'resultados'}</h2><span class="muted tiny">Ordenados por relevância</span></div>
      <div class="search-results">${results.map(b=>`<article class="result-row" style="--cover-a:${b.a};--cover-b:${b.b}"><div class="result-cover"></div><div><h3>${esc(b.title)}</h3><p>${esc(b.author)} • ${b.genre}</p><div class="result-tags">${b.keywords.slice(0,3).map(k=>`<span class="pill">${k}</span>`).join('')}</div></div><div class="result-actions"><button class="secondary-btn" data-save="${b.id}">${icon('bookmark','icon-sm')} ${state.saved[b.id]?'Salvo':'Salvar'}</button><button class="primary-btn" data-open-book="${b.id}">Ler</button></div></article>`).join('')||`<div class="panel-card"><h3>Nenhum livro encontrado</h3><p class="muted">Tente outra palavra ou remova o filtro atual.</p></div>`}</div></main>`;
    shell(content,'Buscar');
    const inp=document.getElementById('searchInput'); inp.oninput=e=>{state.search=e.target.value;renderSearch();const i=document.getElementById('searchInput');i.focus();i.setSelectionRange(i.value.length,i.value.length)};
    document.querySelectorAll('[data-filter]').forEach(b=>b.onclick=()=>{state.searchFilter=b.dataset.filter;renderSearch()});
    document.querySelectorAll('[data-open-book]').forEach(b=>b.onclick=()=>openBook(b.dataset.openBook));
    document.querySelectorAll('[data-save]').forEach(b=>b.onclick=()=>{state.saved[b.dataset.save]=!state.saved[b.dataset.save];save();renderSearch()});
  }

  async function openBook(id){state.readerBook=id;state.route='reader'; try{await window.__BOOKRUSH_API__?.viewable?.(id);await window.__BOOKRUSH_API__?.open?.(id);window.__BOOKRUSH_CHAPTERS__=await window.__BOOKRUSH_API__?.chapters?.(id)||[]}catch(e){toast(e.message||'Não foi possível registrar a abertura')} state.recent=[id,...(state.recent||[]).filter(x=>x!==id)].slice(0,8);render();}

  function renderReader(){
    const b=book(state.readerBook);
    const content=`<div class="reader-shell" id="readerShell">
      <div class="reader-overlay reader-overlay-left"><button class="reader-back" data-route="${mode==='app'?'feed':'library'}" aria-label="Voltar">${icon('chevron','icon-sm')}<span>Voltar</span></button></div>
      <div class="reader-overlay reader-overlay-center"><span class="reader-title">${esc(b.title)}</span></div>
      <div class="reader-overlay reader-overlay-right"><button class="reader-bookmark" id="bookmarkBtn" aria-label="Marcar posição">${icon('bookmark','icon-sm')}</button></div>
      <main class="reader-main"><div class="readium-host" id="readium-reader" aria-label="Leitor Readium"></div></main>
    </div>`;
    shell(content,b.title);
    const host=document.getElementById('readium-reader');
    if(host && window.__BOOKRUSH_READIUM__){ window.__BOOKRUSH_READIUM__.mount(state.readerBook,host,p=>{ const percent=Math.round((p||0)*100); if(percent>0){state.readerPage=percent;window.__BOOKRUSH_API__?.progress?.(state.readerBook,percent,percent).catch(()=>{});}}).catch(e=>{host.innerHTML=`<p class="notice error">${esc(e.message||'Não foi possível abrir a publicação Readium.')}</p>`;}); }
    document.getElementById('bookmarkBtn')?.addEventListener('click',async()=>{await window.__BOOKRUSH_API__?.bookmark?.(state.readerBook,Math.round(state.readerPage));save();toast(`Página marcada em ${state.readerPage}%`)});
  }

  function renderLibrary(){
    const tabs=[['saved','Salvos'],['reading','Em leitura'],['recent','Recentes']];
    let list=state.libraryTab==='saved'?BOOKS.filter(b=>state.saved[b.id]):state.libraryTab==='reading'?BOOKS.filter(b=>b.progress>0):(state.recent||[]).map(book);
    const content=`<main class="content"><div class="page-heading"><div><div class="eyebrow">SUA ESTANTE</div><h1>Minha biblioteca</h1><p>Livros salvos, leituras em andamento e itens abertos recentemente.</p></div></div>
      <div class="tab-row">${tabs.map(([id,l])=>`<button class="tab-btn ${state.libraryTab===id?'active':''}" data-tab="${id}">${l}</button>`).join('')}</div>
      <div class="library-grid">${list.map(b=>`<article class="library-item" data-open-book="${b.id}" style="--cover-a:${b.a};--cover-b:${b.b}"><div class="library-cover"></div><div><h3>${esc(b.title)}</h3><p>${esc(b.author)}</p><div class="library-progress"><span style="width:${b.progress||12}%"></span></div><span class="library-percent">${b.progress||12}% lido</span></div></article>`).join('')||`<div class="panel-card"><h3>Nada aqui ainda</h3><p class="muted">Salve livros no feed para vê-los nesta seção.</p></div>`}</div>
      <div class="section-row"><h2>Retomar rapidamente</h2></div><div class="book-grid">${BOOKS.filter(b=>b.progress>20).slice(0,5).map(bookCard).join('')}</div></main>`;
    shell(content,'Biblioteca');
    document.querySelectorAll('[data-tab]').forEach(b=>b.onclick=()=>{state.libraryTab=b.dataset.tab;renderLibrary()});
    document.querySelectorAll('[data-open-book]').forEach(b=>b.onclick=()=>openBook(b.dataset.openBook));
  }

  function renderProfile(){
    const remote=window.__BOOKRUSH_PROFILE__||{};
    const name=remote.display_name||remote.displayName||'Leitor BookRush';
    const subject=remote.subject_key||remote.subject||'identidade autenticada';
    const bio=remote.bio||'';
    const isPublic=remote.is_public!==false && remote.isPublic!==false;
    const content=`<main class="content"><section class="profile-hero"><div class="profile-avatar">${esc(name.slice(0,2).toUpperCase())}</div><div><div class="eyebrow">PERFIL PÚBLICO</div><h1>${esc(name)}</h1><p>${esc(subject)}</p><p class="muted">${esc(bio||'Adicione uma descrição ao seu perfil.')}</p></div><div class="profile-actions"><button class="secondary-btn" data-route="privacy">${icon('settings','icon-sm')} Privacidade</button><button class="secondary-btn" id="shareProfile">${icon('share','icon-sm')}</button></div></section>
      <section class="form-card"><h3>Editar perfil</h3><form id="profileForm" class="form-grid"><div class="field full"><label for="profileName">Nome de exibição</label><input id="profileName" maxlength="160" value="${esc(name)}" /></div><div class="field full"><label for="profileBio">Biografia</label><textarea id="profileBio" maxlength="2000" rows="4">${esc(bio)}</textarea></div><div class="field full"><button class="primary-btn" type="submit">Salvar perfil</button><span id="profileStatus" class="muted tiny"></span></div></form></section></main>`;
    shell(content,name);
    document.getElementById('shareProfile').onclick=()=>openShare('profile');
    document.getElementById('profileForm').onsubmit=async e=>{e.preventDefault();const status=document.getElementById('profileStatus');try{status.textContent='Salvando…';window.__BOOKRUSH_PROFILE__=await window.__BOOKRUSH_API__?.updateProfile?.({displayName:document.getElementById('profileName').value.trim(),bio:document.getElementById('profileBio').value.trim(),isPublic});status.textContent='Perfil salvo';toast('Perfil atualizado')}catch(err){status.textContent='';toast(err.message||'Não foi possível salvar o perfil')}};
  }

  function renderPrivacy(){
    const current=window.__BOOKRUSH_PROFILE__||{};
    const publicProfile=current.is_public!==false && current.isPublic!==false;
    const content=`<main class="content"><div class="page-heading"><div><div class="eyebrow">CONTROLE DE VISIBILIDADE</div><h1>Privacidade do perfil</h1><p>Esta configuração é persistida pelo reader-profile-service.</p></div><button class="primary-btn" id="savePrivacy">Salvar alterações</button></div><section class="settings-grid"><section class="setting-card"><div class="setting-row"><div><strong>Perfil público</strong><span>Permite que outros leitores encontrem seu perfil quando o contrato público estiver habilitado.</span></div><button class="switch ${publicProfile?'on':''}" id="publicProfile" aria-label="Alternar perfil público"></button></div><p class="muted">Preferências detalhadas de atividade, biblioteca e seguidores serão adicionadas quando os respectivos campos existirem no contrato.</p></section></section></main>`;
    shell(content,'Privacidade');
    let enabled=publicProfile;
    document.getElementById('publicProfile').onclick=()=>{enabled=!enabled;document.getElementById('publicProfile').classList.toggle('on',enabled)};
    document.getElementById('savePrivacy').onclick=async()=>{try{window.__BOOKRUSH_PROFILE__=await window.__BOOKRUSH_API__?.updateProfile?.({displayName:current.display_name||current.displayName||'',bio:current.bio||'',isPublic:enabled});toast('Privacidade salva')}catch(e){toast(e.message||'Não foi possível salvar a privacidade')}};
  }

  function renderPlant(){
    const remote=window.__BOOKRUSH_STREAK__||{};
    const days=Number(remote.currentDays||remote.current_days||0);
    const minutes=Number(remote.minutesToday||remote.minutes_today||0);
    const content=`<main class="content"><div class="page-heading"><div><div class="eyebrow">HÁBITO DE LEITURA</div><h1>Sua planta de leitura</h1><p>Esta visão usa o streak persistido no reader-state-service.</p></div></div><section class="plant-metric"><div class="eyebrow">SEQUÊNCIA ATUAL</div><div class="metric-number">${days} dias</div><p>${minutes} minutos registrados hoje.</p><div class="notice">Dados de crescimento visual detalhado ainda dependem do contrato de projeção da planta.</div></section></main>`;
    shell(content,'Minha planta');
  }

    function renderOtherProfile(){
    const content=`<main class="content"><section class="locked-profile"><div class="lock-orb">${icon('shield','icon-xl')}</div><h2>Perfil de outro leitor</h2><p>A consulta de perfis públicos será habilitada quando o contrato de descoberta social estiver disponível. Nenhum dado fictício é exibido.</p><button class="secondary-btn" data-route="feed">Voltar ao feed</button></section></main>`;
    shell(content,'Perfil');
  }

    function renderAdmin(){
    const users=window.__BOOKRUSH_ADMIN_USERS__||[], reports=window.__BOOKRUSH_ADMIN_REPORTS__||[];
    const tabs=[['overview','Visão geral'],['users','Usuários'],['moderation','Moderação']];
    const tabbar=`<div class="tab-row admin-tabs">${tabs.map(([id,l])=>`<button class="tab-btn ${state.adminTab===id?'active':''}" data-admin-tab="${id}">${l}</button>`).join('')}</div>`;
    let body='';
    if(state.adminTab==='overview') body=`<div class="dashboard-grid"><article class="metric-card"><div class="metric-label"><span>Usuários consultáveis</span>${icon('user','icon-sm')}</div><strong>${users.length}</strong><span class="metric-change">retorno atual da facade administrativa</span></article><article class="metric-card"><div class="metric-label"><span>Denúncias abertas</span>${icon('warning','icon-sm')}</div><strong>${reports.length}</strong><span class="metric-change">fila persistida de moderação</span></article></div><section class="panel-card"><h3>Operação</h3><p class="muted">As métricas comportamentais detalhadas são fornecidas pelos serviços de behavior e analytics. Esta tela mostra somente dados retornados pela facade administrativa.</p></section>`;
    if(state.adminTab==='users') body=`<div class="page-heading compact-heading"><div><h2>Gerenciamento de usuários</h2><p>Identidades e perfis retornados pela API administrativa.</p></div><div class="search-box mini-search">${icon('search','icon-sm')}<input id="adminUserSearch" placeholder="Buscar usuário..." /></div></div><div class="table-wrap"><table class="data-table"><thead><tr><th>Usuário</th><th>Visibilidade</th><th>Atualizado</th><th>Ações</th></tr></thead><tbody id="adminUsersBody">${adminUserRows()}</tbody></table></div>`;
    if(state.adminTab==='moderation') body=`<div class="page-heading compact-heading"><div><h2>Fila de moderação</h2><p>Decisões são persistidas no admin-service.</p></div><span class="pill">${reports.length} pendentes</span></div><div class="moderation-list">${moderationCards()}</div>`;
    const content=`<main class="content"><div class="page-heading"><div><div class="eyebrow">OPERAÇÃO DA REDE</div><h1>Administração</h1><p>Dados carregados do backend, sem valores de demonstração.</p></div></div>${tabbar}${body}</main>`;
    shell(content,'Administração');
    document.querySelectorAll('[data-admin-tab]').forEach(b=>b.onclick=()=>{state.adminTab=b.dataset.adminTab;save();renderAdmin()});
    const input=document.getElementById('adminUserSearch'); if(input) input.oninput=()=>{document.getElementById('adminUsersBody').innerHTML=adminUserRows(input.value)};
    bindAdminActions();
  }

  function adminUserRows(q=''){
    const remote=window.__BOOKRUSH_ADMIN_USERS__||[];
    return remote.filter(u=>String(u.display_name||u.subject_key||'').toLowerCase().includes(q.toLowerCase())).map(u=>{
      const id=String(u.subject_key||''); const name=String(u.display_name||id); const initials=name.slice(0,2).toUpperCase();
      return `<tr><td><div class="row-user"><div class="avatar">${esc(initials)}</div><div><strong>${esc(name)}</strong><div class="muted tiny">${esc(id)}</div></div></div></td><td>${u.is_public===false?'Privado':'Público'}</td><td>${esc(String(u.updated_at||'—'))}</td><td><button class="mini-btn" data-inspect-user="${esc(id)}">Detalhes</button></td></tr>`;
    }).join('')||'<tr><td colspan="4" class="muted">Nenhum usuário retornado.</td></tr>';
  }

  function bindAdminActions(){
    document.querySelectorAll('[data-inspect-user]').forEach(button=>button.onclick=()=>{
      const id=button.dataset.inspectUser; const u=(window.__BOOKRUSH_ADMIN_USERS__||[]).find(x=>String(x.subject_key||'')===id);
      if(!u)return;
      modal(`<div class="eyebrow">DETALHES DO USUÁRIO</div><h2>${esc(String(u.display_name||id))}</h2><p>${esc(id)}</p><p>${u.is_public===false?'Perfil privado':'Perfil público'}</p>`,`<button class="secondary-btn" data-close-modal>Fechar</button>`); bindModalClose();
    });
    document.querySelectorAll('[data-report-action]').forEach(button=>button.onclick=async()=>{const [decision,id]=button.dataset.reportAction.split(':');try{await window.__BOOKRUSH_API__?.adminModerate?.(id,decision==='remove'?'REMOVE':'ARCHIVE');toast('Decisão registrada');const r=await window.__BOOKRUSH_API__?.adminReports?.();window.__BOOKRUSH_ADMIN_REPORTS__=r||[];renderAdmin();}catch(e){toast(e.message||'Não foi possível registrar a decisão')}});
  }

  function moderationCards(){
    const remote=window.__BOOKRUSH_ADMIN_REPORTS__||[];
    const reports=remote.length?remote.map(r=>[r.id,'Denúncia',r.book_id||'Livro',r.comment_id||'Comentário reportado',r.reason||'Motivo não informado']):[];
    const visible=reports.filter(r=>!state.removedReports[r[0]]); if(!visible.length)return `<div class="empty-state"><div class="lock-orb">✓</div><h2>Fila limpa</h2><p>Não há denúncias pendentes.</p></div>`;
    return visible.map(r=>`<article class="report-card"><div class="report-head"><div><strong>${r[1]}</strong><span>${r[2]}</span></div><span>há ${r[0]==='r1'?'8 min':r[0]==='r2'?'31 min':'2 h'}</span></div><div class="report-quote">${r[3]}</div><div class="report-reason">Motivo informado: ${r[4]}</div><div class="report-actions"><button class="danger-btn" data-report-action="remove:${r[0]}">Remover conteúdo</button><button class="secondary-btn" data-report-action="archive:${r[0]}">Manter e arquivar</button></div></article>`).join('');
  }

  function renderPublisher(){
    const tabs=[['publish','Publicar livro'],['analytics','Estatísticas']];
    const tabbar=`<div class="tab-row">${tabs.map(([id,l])=>`<button class="tab-btn ${state.publisherTab===id?'active':''}" data-publisher-tab="${id}">${l}</button>`).join('')}</div>`;
    const content=`<main class="content"><div class="page-heading"><div><div class="eyebrow">PORTAL DO PUBLICADOR</div><h1>${state.publisherTab==='publish'?'Nova publicação':'Desempenho dos livros'}</h1><p>${state.publisherTab==='publish'?'Cadastre os metadados e envie os arquivos do livro.':'Acompanhe visualizações, leituras e interações das suas publicações.'}</p></div></div>${tabbar}${state.publisherTab==='publish'?publisherForm():publisherAnalytics()}</main>`;
    shell(content,'Publicador');
    document.querySelectorAll('[data-publisher-tab]').forEach(b=>b.onclick=()=>{state.publisherTab=b.dataset.publisherTab;save();renderPublisher()});
    bindPublisher();
  }

  function publisherForm(){
    return `<div class="publisher-layout"><section class="form-card"><h3>Nova publicação</h3><p class="muted">O título cria o rascunho no servidor. Depois, o arquivo é enviado diretamente ao storage privado por uma URL temporária.</p><form id="publishForm" class="form-grid"><div class="field full"><label>Título</label><input id="pubTitle" required placeholder="Título da obra" /></div><div class="field full"><label>Arquivo EPUB/PDF</label><input id="pubFile" type="file" required accept=".epub,.pdf,application/epub+zip,application/pdf" /></div><div class="field full"><div class="form-actions"><button type="button" class="secondary-btn" id="saveDraft">Salvar rascunho</button><button type="submit" class="primary-btn">Enviar publicação ${icon('arrow','icon-sm')}</button></div><span id="publishStatus" class="muted tiny"></span></div></form></section><section class="panel-card"><h3>Publicações recentes</h3><div id="publisherSubmissionList"><p class="muted">Carregando dados reais…</p></div></section></div>`;
  }

  function publisherAnalytics(){
    const rows=window.__BOOKRUSH_PUBLISHER_SUBMISSIONS__||[];
    const content=rows.length ? rows.map(b=>'<div class="analytics-book-row"><div><strong>'+esc(b.title||'Sem título')+'</strong><span>'+esc(b.status||'UNKNOWN')+(b.catalog_book_id?' · catálogo ligado':' · aguardando canonização')+'</span></div><div class="analytics-number">—<small>visualizações</small></div><div class="analytics-number">—<small>leituras</small></div><div class="analytics-number"><button class="mini-btn" data-metrics-id="'+esc(b.id||'')+'">Métricas</button> <button class="mini-btn" data-submit-id="'+esc(b.id||'')+'">'+(b.status==='SUBMITTED'?'Enviado':'Enviar para revisão')+'</button></div></div>').join('') : '<p class="muted">Nenhuma publicação encontrada para este usuário.</p>';
    return `<section class="panel-card"><div class="section-row" style="margin:0"><h2>Publicações</h2><span class="muted tiny">Dados do publisher-service</span></div><div class="analytics-list">${content}</div></section>`;
  }

  function bindPublisher(){
    const title=document.getElementById('pubTitle');
    document.getElementById('saveDraft')?.addEventListener('click',async()=>{try{await window.__BOOKRUSH_API__?.createSubmission?.(title.value.trim());toast('Rascunho salvo no servidor');renderPublisher()}catch(e){toast(e.message||'Não foi possível salvar')}});
    document.getElementById('publishForm')?.addEventListener('submit',async e=>{e.preventDefault();const file=document.getElementById('pubFile')?.files?.[0];if(!file)return;const status=document.getElementById('publishStatus');try{status.textContent='Criando rascunho…';const created=await window.__BOOKRUSH_API__?.createSubmission?.(title.value.trim());if(!created?.id)throw new Error('O serviço não retornou a submissão.');status.textContent='Enviando arquivo…';await window.__BOOKRUSH_API__?.uploadSubmissionFile?.(created.id,file);status.textContent='Enviando para revisão…';await window.__BOOKRUSH_API__?.submitSubmission?.(created.id);toast('Publicação enviada para revisão');state.publisherTab='analytics';renderPublisher()}catch(err){status.textContent='';toast(err.message||'Não foi possível enviar')}});
    document.querySelectorAll('[data-submit-id]').forEach(button=>button.onclick=async()=>{try{await window.__BOOKRUSH_API__?.submitSubmission?.(button.dataset.submitId);toast('Enviado para revisão');renderPublisher()}catch(e){toast(e.message||'Não foi possível enviar')}});
    document.querySelectorAll('[data-metrics-id]').forEach(button=>button.onclick=async()=>{try{const result=await window.__BOOKRUSH_API__?.publisherMetrics?.(button.dataset.metricsId);const rows=result?.items||[];modal('<div class="eyebrow">MÉTRICAS REAIS</div><h2>Leituras da publicação</h2><p>'+esc(result?.status==='NOT_LINKED'?'A submissão ainda não está ligada a um livro canônico.':`Dados agregados por behavior-service (${rows.length} dias).` )+'</p>'+rows.slice(0,30).map(r=>'<div class="setting-row"><strong>'+esc(String(r.day))+'</strong><span>aberturas '+r.opens+' · leituras '+r.reads+' · curtidas '+r.likes+'</span></div>').join(''));}catch(e){toast(e.message||'Não foi possível carregar métricas')}});
  }

  function modal(body,actions=''){
    document.querySelector('.modal-wrap')?.remove();
    const wrap=document.createElement('div');wrap.className='modal-wrap';wrap.innerHTML=`<section class="modal">${body}<div class="modal-actions">${actions||'<button class="primary-btn" data-close-modal>Fechar</button>'}</div></section>`;document.body.appendChild(wrap);wrap.addEventListener('click',e=>{if(e.target===wrap)wrap.remove()});bindModalClose();
  }
  function bindModalClose(){document.querySelectorAll('[data-close-modal]').forEach(b=>b.onclick=()=>document.querySelector('.modal-wrap')?.remove())}

  async function openComments(id){
    const b=book(id); let comments=[]; try{comments=await (window.__BOOKRUSH_API__?.comments?.(id)||[])}catch(e){toast(e.message||'Não foi possível carregar comentários')}
    const rows=Array.isArray(comments)?comments:[];
    const overlay=document.createElement('div');overlay.className='overlay';overlay.innerHTML=`<aside class="drawer"><div class="drawer-head"><div><h3>Comentários</h3><span class="muted tiny">${esc(b.title)}</span></div><button class="icon-btn" data-close-drawer>${icon('close')}</button></div><div class="drawer-body">${rows.map(c=>`<div class="comment"><div class="avatar">${esc(c.authorInitials||'•')}</div><div><strong>${esc(c.authorName||c.subject_key||'Leitor')}</strong><p>${esc(c.body||c.text||'')}</p></div></div>`).join('')||'<p class="muted">Ainda não há comentários.</p>'}</div><div class="comment-compose"><input id="commentInput" placeholder="Escreva um comentário..."/><button class="primary-btn" id="commentSend">Enviar</button></div></aside>`;document.body.appendChild(overlay);
    overlay.querySelector('[data-close-drawer]').onclick=()=>overlay.remove(); overlay.addEventListener('click',e=>{if(e.target===overlay)overlay.remove()}); overlay.querySelector('#commentSend').onclick=async()=>{const i=overlay.querySelector('#commentInput');if(!i.value.trim())return;try{await window.__BOOKRUSH_API__?.comment?.(id,i.value.trim());const counts=await window.__BOOKRUSH_API__?.refreshCounts?.(id);toast('Comentário publicado');overlay.remove();const counter=document.querySelector(`[data-comments=\"${id}\"] .action-count`);if(counter&&counts)counter.textContent=compact(counts.comments);openComments(id)}catch(e){toast(e.message||'Não foi possível publicar o comentário')}};
  }

  function openShare(id){
    const label=id==='profile'?'este perfil':`“${book(id).title}”`;
    modal(`<div class="eyebrow">COMPARTILHAR</div><h2>Envie ${label}</h2><p>Escolha uma opção para compartilhar.</p><div class="share-grid"><button class="share-choice" data-share-choice><strong>↗</strong>Copiar link</button><button class="share-choice" data-share-choice><strong>•••</strong>Mais</button></div>`,`<button class="secondary-btn" data-close-modal>Cancelar</button>`);
    document.querySelectorAll('[data-share-choice]').forEach(b=>b.onclick=async()=>{try{await window.__BOOKRUSH_API__?.share?.(id);await navigator.clipboard?.writeText(`${window.location.origin}/book/${id}`);toast('Compartilhamento registrado')}catch(e){toast(e.message||'Não foi possível compartilhar')}document.querySelector('.modal-wrap')?.remove()});
  }

  window.addEventListener('keydown',e=>{if((e.metaKey||e.ctrlKey)&&e.key.toLowerCase()==='k'){e.preventDefault();go('search')}});
  render();
})();
