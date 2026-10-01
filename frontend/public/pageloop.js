(() => {
  const mode = document.body.dataset.mode || 'web';
  const initialRoute = document.body.dataset.start || 'feed';

  const BOOKS = window.__BOOKRUSH_BOOKS__ || [];

  const USERS = [
    {id:'u1',name:'Clara Nunes',handle:'@claralê',initials:'CN',bio:'Leio clássicos, ficção científica e qualquer coisa que tenha uma boa primeira frase.',followers:'18,4 mil',following:'524',books:'126',private:false},
    {id:'u2',name:'Rafael Mori',handle:'@rafamori',initials:'RM',bio:'Fantasia, café e finais ambíguos.',followers:'7,8 mil',following:'302',books:'89',private:false},
    {id:'u3',name:'Lia Campos',handle:'@liac',initials:'LC',bio:'Clube do livro aos domingos.',followers:'3,1 mil',following:'198',books:'57',private:true}
  ];

  const COMMENTS = [
    {name:'Clara Nunes',initials:'CN',time:'2 min',text:'Esse trecho foi exatamente o que me fez abrir o livro. A atmosfera é ótima.'},
    {name:'Rafael Mori',initials:'RM',time:'18 min',text:'A tradução muda bastante o ritmo, mas a ideia continua muito forte.'},
    {name:'Maya Luz',initials:'ML',time:'1 h',text:'Salvei para começar no fim de semana 👀'},
    {name:'Davi A.',initials:'DA',time:'3 h',text:'Alguém mais ficou preso nesse livro por causa de uma única citação?'}
  ];

  const defaultState = {
    route: initialRoute,
    likes:{}, saved:Object.fromEntries(Array.from(window.__BOOKRUSH_API__?.saved || []).map(id=>[id,true])), followed:{}, blocked:{}, removedReports:{},
    readerBook:'b1', readerPage: 43, readerSize:20, readerNight:false,
    libraryTab:'saved', search:'', searchFilter:'Todos', streak:12, minutes:184, booksMonth:4,
    privacy:{activity:true,library:true,streak:true,followers:true}, recent:['b3','b1','b5'], adminTab:'overview', publisherTab:'publish', otherUser:'u2',
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
    ${mode==='web'?`<div class="prototype-switcher"><a class="prototype-pill" href="${state.route==='admin'?'admin.html':state.route==='publisher'?'publisher.html':'app.html'}">${state.route==='admin'||state.route==='publisher'?'BookRush':'BookRush Mobile'}</a></div>`:''}`;
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
  function go(route){state.route=route;render();}

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
        <div class="feed-meta"><span class="genre-chip">${b.genre}</span><span class="match">${b.match}% para você</span><span>•</span><span>${Math.max(2,8-idx)} min de trecho</span></div>
        <div class="quote-mark">“</div><div class="feed-quote">${esc(b.quote)}</div>
        <div class="book-line"><div class="mini-cover"></div><div><strong>${esc(b.title)}</strong><span>${esc(b.author)}</span></div></div>
        <button class="primary-btn open-read" data-open-book="${b.id}">${icon('play','icon-sm')} Ler agora</button>
      </div>
      <div class="action-rail">
        <button class="action ${state.likes[b.id]?'liked':''}" data-like="${b.id}"><span class="action-bubble">${icon('heart','icon-lg')}</span><span class="action-count">${compact(b.likes+(state.likes[b.id]?1:0))}</span></button>
        <button class="action" data-comments="${b.id}"><span class="action-bubble">${icon('comment','icon-lg')}</span><span class="action-count">${compact(b.comments)}</span></button>
        <button class="action ${state.saved[b.id]?'saved':''}" data-save="${b.id}"><span class="action-bubble">${icon('bookmark','icon-lg')}</span><span class="action-count">${state.saved[b.id]?'Salvo':'Salvar'}</span></button>
        <button class="action" data-share="${b.id}"><span class="action-bubble">${icon('share','icon-lg')}</span><span class="action-count">${compact(b.shares)}</span></button>
      </div>
    </article>`;
  }

  function renderFeed(){
    const main=`<div class="feed-layout"><section class="feed-stage"><div class="feed-scroll" id="feedScroll">${BOOKS.slice(0,6).map(feedCard).join('')}</div></section>
      <aside class="feed-side">
        <div class="side-card"><div class="streak-row"><div class="streak-flame">🔥</div><div><h3>${state.streak} dias de sequência</h3><p>Mais 16 min hoje para manter seu ritmo.</p></div></div><div class="progress-track"><div class="progress-fill" style="width:68%"></div></div></div>
        <div class="side-card"><h3>Seu radar de leitura</h3><p>O feed está priorizando temas que você costuma abrir e concluir.</p><div class="taste-row"><span class="taste">Clássicos 84%</span><span class="taste">Sci-fi 79%</span><span class="taste">Gótico 66%</span><span class="taste">Ensaios 54%</span></div></div>
        <div class="side-card"><h3>Leitores para seguir</h3>${USERS.map(u=>`<div class="quick-user"><button class="avatar avatar-button" data-user-profile="${u.id}">${u.initials}</button><div class="quick-user-info"><button class="user-link" data-user-profile="${u.id}"><strong>${u.name}</strong><span>${u.handle}</span></button></div><button class="follow-mini" data-follow="${u.id}">${state.followed[u.id]?'Seguindo':'Seguir'}</button></div>`).join('')}</div>
        <div class="side-card"><h3>Como usar o protótipo</h3><p>Role verticalmente como em um feed de vídeos. Curtidas, salvos e marcações permanecem após atualizar a página.</p></div>
      </aside></div>`;
    shell(main,'Para você'); bindFeed();
  }

  function bindFeed(){
    document.querySelectorAll('[data-like]').forEach(b=>b.onclick=async()=>{state.likes[b.dataset.like]=!state.likes[b.dataset.like];try{await window.__BOOKRUSH_API__?.like(b.dataset.like,state.likes[b.dataset.like]);save();renderFeed();}catch(e){state.likes[b.dataset.like]=!state.likes[b.dataset.like];toast(e.message||'Não foi possível curtir');}});
    document.querySelectorAll('[data-save]').forEach(b=>b.onclick=async()=>{state.saved[b.dataset.save]=!state.saved[b.dataset.save];try{await window.__BOOKRUSH_API__?.save(b.dataset.save,state.saved[b.dataset.save]);save();toast(state.saved[b.dataset.save]?'Adicionado à sua biblioteca':'Removido da biblioteca');renderFeed();}catch(e){state.saved[b.dataset.save]=!state.saved[b.dataset.save];toast(e.message||'Não foi possível atualizar a biblioteca');}});
    document.querySelectorAll('[data-comments]').forEach(b=>b.onclick=()=>openComments(b.dataset.comments));
    document.querySelectorAll('[data-share]').forEach(b=>b.onclick=()=>openShare(b.dataset.share));
    document.querySelectorAll('[data-open-book]').forEach(b=>b.onclick=()=>openBook(b.dataset.openBook));
    document.querySelectorAll('[data-follow]').forEach(b=>b.onclick=()=>{state.followed[b.dataset.follow]=!state.followed[b.dataset.follow];save();renderFeed();});
    document.querySelectorAll('[data-user-profile]').forEach(b=>b.onclick=()=>{state.otherUser=b.dataset.userProfile;state.route='otherProfile';render();});
    const fs=document.getElementById('feedScroll'); if(fs){document.onkeydown=e=>{if(!['ArrowDown','ArrowUp'].includes(e.key))return;fs.scrollBy({top:(e.key==='ArrowDown'?1:-1)*fs.clientHeight,behavior:'smooth'})}}
  }

  function bookCard(b){return `<article class="book-card" style="--cover-a:${b.a};--cover-b:${b.b}" data-open-book="${b.id}"><div class="book-cover"><span class="cover-mark">PAGELOOP EDITION</span><span class="cover-title">${esc(b.title)}</span></div><h3>${esc(b.title)}</h3><p>${esc(b.author)}</p><div class="book-card-meta"><span>${b.genre}</span><span>${b.match}% match</span></div></article>`}

  function renderRecommendations(){
    const content=`<main class="content"><div class="page-heading"><div><div class="eyebrow">CURADORIA PERSONALIZADA</div><h1>Descubra sua próxima leitura</h1><p>Recomendações mockadas a partir do seu histórico de abertura, leitura e salvamentos.</p></div><button class="primary-btn" id="luckyBtn">${icon('sparkle')} Estou com sorte</button></div>
      <section class="hero-strip"><div><div class="eyebrow">SEU PERFIL DE LEITURA</div><h2>Você tende a ficar mais tempo em narrativas contemplativas com tensão social.</h2><p>O protótipo usa esse resumo apenas como recurso visual. Num produto real, a recomendação seria explicável por sinais de uso e metadados dos livros.</p><div class="hero-actions"><span class="pill">Clássicos</span><span class="pill">Ficção científica</span><span class="pill">Atmosfera</span><span class="pill">Personagens complexos</span></div></div><div class="taste-orb"><div><div><strong>92</strong><span>afinidade média</span></div></div></div></section>
      <div class="section-row"><h2>Escolhidos para você</h2><button class="ghost-btn">Ajustar interesses</button></div><div class="book-grid">${BOOKS.slice(0,5).map(bookCard).join('')}</div>
      <div class="section-row"><h2>Porque você gostou de Frankenstein</h2><span class="muted tiny">Baseado em temas e comportamento</span></div><div class="book-grid">${[BOOKS[8],BOOKS[4],BOOKS[1],BOOKS[6],BOOKS[2]].map(bookCard).join('')}</div></main>`;
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

  async function openBook(id){state.readerBook=id;state.route='reader'; try{await window.__BOOKRUSH_API__?.open?.(id)}catch(e){toast(e.message||'Não foi possível abrir o livro')} state.readerPage=book(id).progress||state.readerPage;state.recent=[id,...(state.recent||[]).filter(x=>x!==id)].slice(0,8);save();render();}

  const PARAS = [
    'A manhã entrou pela janela com a delicadeza de quem não queria interromper nada. Havia um silêncio incomum no jardim, interrompido apenas pelas folhas que se tocavam com o vento.',
    'Ela avançou devagar, como se cada passo pudesse revelar alguma coisa que estivera escondida por muito tempo. O caminho parecia familiar e novo ao mesmo tempo, e essa contradição a fazia sorrir.',
    'Naquele instante, não era importante entender tudo. Bastava perceber que algumas mudanças começam pequenas: uma porta entreaberta, uma frase sublinhada, uma ideia que insiste em voltar depois que a página termina.',
    'O livro repousava aberto, com a lombada marcada pelo uso. Não havia pressa. Ler era, por alguns minutos, escolher uma velocidade diferente para o mundo.',
    'Quando voltou os olhos para a janela, a luz havia mudado. O mesmo lugar já não parecia exatamente o mesmo, e talvez fosse essa a forma mais discreta de uma história continuar fora do papel.',
    'Ela marcou a página antes de fechar o volume. Sabia que retornaria, não porque tivesse obrigação de terminar, mas porque alguma coisa naquele trecho ainda permanecia em movimento.'
  ];

  function renderReader(){
    const b=book(state.readerBook); const content=`<div class="reader-shell ${state.readerNight?'night':''}" id="readerShell">
      <aside class="reader-panel"><button class="reader-tool" data-route="${mode==='app'?'feed':'library'}">${icon('chevron','icon-sm')} Voltar <span></span></button><h3>Conteúdo</h3>${['Abertura','Capítulo I','Capítulo II','Capítulo III','Capítulo IV','Notas'].map((x,i)=>`<div class="toc-item ${i===1?'active':''}">${x}</div>`).join('')}</aside>
      <article class="reader-main"><div class="reader-book-meta"><div class="eyebrow">${b.genre}</div><h1>${esc(b.title)}</h1><p>${esc(b.author)}</p></div><div class="reader-text" style="--reader-size:${state.readerSize}px">${PARAS.concat(PARAS.slice(0,3)).map(p=>`<p>${p}</p>`).join('')}</div></article>
      <aside class="reader-panel right"><h3>Leitura</h3><button class="reader-tool" id="bookmarkBtn">${icon('bookmark','icon-sm')} Marcar página <span>${state.readerPage}%</span></button><button class="reader-tool" id="nightBtn">${icon('sun','icon-sm')} Tema <span>${state.readerNight?'Noturno':'Papel'}</span></button><button class="reader-tool" id="fontDown">${icon('type','icon-sm')} Texto menor <span>A−</span></button><button class="reader-tool" id="fontUp">${icon('type','icon-sm')} Texto maior <span>A+</span></button><div class="reader-stat"><strong>24 min</strong><span>tempo de leitura nesta sessão</span></div><div class="reader-stat"><strong>38 min</strong><span>estimativa até o fim</span></div><p class="bookmark-note">A posição é salva localmente neste protótipo e permanece após recarregar a página.</p></aside>
      <div class="reader-progress"><div class="reader-progress-top"><span>Página ${Math.round(state.readerPage/100*b.pages)} de ${b.pages}</span><span>${state.readerPage}%</span></div><input class="reader-slider" id="readerSlider" type="range" min="1" max="100" value="${state.readerPage}" /></div>
    </div>`;
    shell(content,b.title);
    document.getElementById('readerSlider').oninput=e=>{state.readerPage=+e.target.value;save();document.querySelector('.reader-progress-top').innerHTML=`<span>Página ${Math.round(state.readerPage/100*b.pages)} de ${b.pages}</span><span>${state.readerPage}%</span>`};
    document.getElementById('bookmarkBtn')?.addEventListener('click',()=>{save();toast(`Página marcada em ${state.readerPage}%`)});
    document.getElementById('nightBtn')?.addEventListener('click',()=>{state.readerNight=!state.readerNight;save();renderReader()});
    document.getElementById('fontDown')?.addEventListener('click',()=>{state.readerSize=Math.max(16,state.readerSize-1);save();renderReader()});
    document.getElementById('fontUp')?.addEventListener('click',()=>{state.readerSize=Math.min(27,state.readerSize+1);save();renderReader()});
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
    const u=USERS[0];
    const content=`<main class="content"><section class="profile-hero"><div class="profile-avatar">${u.initials}</div><div><div class="eyebrow">PERFIL PÚBLICO</div><h1>${u.name}</h1><p>${u.handle} • ${u.bio}</p><div class="profile-stats"><div><strong>${u.followers}</strong><span>seguidores</span></div><div><strong>${u.following}</strong><span>seguindo</span></div><div><strong>${u.books}</strong><span>livros</span></div></div></div><div class="profile-actions"><button class="secondary-btn" data-route="privacy">${icon('settings','icon-sm')} Privacidade</button><button class="secondary-btn" id="shareProfile">${icon('share','icon-sm')}</button></div></section>
      <div class="section-row"><h2>Atividade compartilhada</h2><span class="muted tiny">Visível conforme preferências de privacidade</span></div><div class="activity-grid"><section class="activity-card"><h3>Atividade recente</h3>${[['Terminou','Orgulho e Preconceito','há 2 dias'],['Salvou','Frankenstein','há 4 dias'],['Curtiu um trecho de','Walden','há 1 semana']].map(a=>`<div class="activity-item"><div class="activity-dot"></div><div><p><strong>${a[0]}</strong> ${a[1]}</p><span>${a[2]}</span></div></div>`).join('')}</section><section class="activity-card"><h3>Gosto de leitura</h3><div class="taste-row"><span class="taste">Clássicos</span><span class="taste">Romance</span><span class="taste">Sci-fi</span><span class="taste">Gótico</span><span class="taste">Ensaios</span></div><div class="section-row" style="margin-top:24px"><h2 style="font-size:14px">Neste mês</h2></div><div class="profile-stats"><div><strong>4</strong><span>concluídos</span></div><div><strong>1.124</strong><span>páginas</span></div><div><strong>9h42</strong><span>leitura</span></div></div></section></div>
      <div class="section-row"><h2>Livros favoritos</h2></div><div class="book-grid">${[BOOKS[2],BOOKS[4],BOOKS[5],BOOKS[0],BOOKS[8]].map(bookCard).join('')}</div></main>`;
    shell(content,u.name);
    document.getElementById('shareProfile').onclick=()=>openShare('profile');
    document.querySelectorAll('[data-open-book]').forEach(b=>b.onclick=()=>openBook(b.dataset.openBook));
  }

  function renderPrivacy(){
    const rows=[['activity','Atividade recente','Livros abertos, concluídos e interações'],['library','Biblioteca pública','Livros salvos e em andamento'],['streak','Sequência de leitura','Seu streak e minutos lidos'],['followers','Contagem de seguidores','Números de seguidores e seguindo']];
    const content=`<main class="content"><div class="page-heading"><div><div class="eyebrow">CONTROLE DE VISIBILIDADE</div><h1>Privacidade do perfil</h1><p>Escolha quais informações outros leitores podem visualizar.</p></div><button class="primary-btn" id="savePrivacy">Salvar alterações</button></div><div class="settings-grid"><section class="setting-card"><h3>Informações compartilhadas</h3>${rows.map(([k,t,d])=>`<div class="setting-row"><div><strong>${t}</strong><span>${d}</span></div><button class="switch ${state.privacy[k]?'on':''}" data-privacy="${k}" aria-label="Alternar ${t}"></button></div>`).join('')}</section><section class="setting-card"><h3>Prévia como visitante</h3><div class="privacy-preview"><strong>Clara Nunes</strong><br><span>@claralê</span><br><br>${state.privacy.activity?'✓ Atividade recente visível':'— Atividade ocultada'}<br>${state.privacy.library?'✓ Biblioteca visível':'— Biblioteca ocultada'}<br>${state.privacy.streak?'✓ Streak visível':'— Streak ocultado'}<br>${state.privacy.followers?'✓ Seguidores visíveis':'— Seguidores ocultados'}</div><div class="setting-row"><div><strong>Perfil privado</strong><span>Novos seguidores precisam de aprovação</span></div><button class="switch"></button></div><div class="setting-row"><div><strong>Permitir comentários</strong><span>Em trechos e atualizações públicas</span></div><button class="switch on"></button></div></section></div></main>`;
    shell(content,'Privacidade');
    document.querySelectorAll('[data-privacy]').forEach(b=>b.onclick=()=>{state.privacy[b.dataset.privacy]=!state.privacy[b.dataset.privacy];renderPrivacy()});
    document.getElementById('savePrivacy').onclick=()=>{save();toast('Preferências de privacidade salvas')};
    document.querySelectorAll('.setting-card .switch:not([data-privacy])').forEach(b=>b.onclick=()=>b.classList.toggle('on'));
  }

  function renderPlant(){
    const content=`<main class="content"><div class="page-heading"><div><div class="eyebrow">HÁBITO DE LEITURA</div><h1>Sua planta está crescendo</h1><p>Ela evolui com consistência, tempo de leitura e livros concluídos.</p></div></div><div class="plant-layout"><section class="plant-stage" id="plantStage"><div class="plant-title"><div class="eyebrow">NÍVEL 4</div><h2>Jasmim leitor</h2><p>Próxima folha em 26 minutos</p></div><div class="plant-glow"></div><div class="plant-pot"><div class="stem"></div><div class="leaf l1"></div><div class="leaf l2"></div><div class="leaf l3"></div><div class="leaf l4"></div><div class="sprout"></div><div class="pot"></div><span class="droplet" style="left:145px;top:60px"></span><span class="droplet" style="left:162px;top:44px;animation-delay:.12s"></span><span class="droplet" style="left:178px;top:69px;animation-delay:.22s"></span></div><button class="secondary-btn water-btn" id="waterBtn">💧 Regar</button></section><div class="stats-stack"><section class="plant-metric"><div class="eyebrow">SEQUÊNCIA ATUAL</div><div class="metric-number">${state.streak} dias</div><p>Seu melhor streak é de 19 dias.</p><div class="week-bars">${[73,42,90,65,100,48,82].map((h,i)=>`<div class="week-bar ${i<6?'done':''}" style="height:${h}%"></div>`).join('')}</div><div class="week-labels">${['S','T','Q','Q','S','S','D'].map(x=>`<span>${x}</span>`).join('')}</div></section><section class="plant-metric"><div class="eyebrow">ESTE MÊS</div><div class="metric-number">${state.minutes} min</div><p>${state.booksMonth} livros concluídos • 73% da meta mensal</p><div class="progress-track"><div class="progress-fill" style="width:73%"></div></div></section><section class="plant-metric"><div class="eyebrow">PRÓXIMA EVOLUÇÃO</div><div style="display:flex;justify-content:space-between;align-items:end"><div><div class="metric-number">74%</div><p>Continue lendo para desbloquear flores.</p></div><span style="font-size:38px">🌼</span></div></section></div></div></main>`;
    shell(content,'Minha planta');
    document.getElementById('waterBtn').onclick=()=>{const p=document.getElementById('plantStage');p.classList.remove('watering');void p.offsetWidth;p.classList.add('watering');toast('Sua planta agradeceu 🌱')};
  }

  function renderOtherProfile(){
    const u=USERS.find(x=>x.id===state.otherUser)||USERS[1];
    const restricted=u.private && !state.followed[u.id];
    const content=`<main class="content"><section class="profile-hero"><div class="profile-avatar">${u.initials}</div><div><div class="eyebrow">${u.private?'PERFIL PRIVADO':'PERFIL DE LEITOR'}</div><h1>${u.name}</h1><p>${u.handle} • ${u.bio}</p><div class="profile-stats"><div><strong>${u.followers}</strong><span>seguidores</span></div><div><strong>${u.following}</strong><span>seguindo</span></div><div><strong>${restricted?'—':u.books}</strong><span>livros</span></div></div></div><div class="profile-actions"><button class="primary-btn" id="otherFollow">${state.followed[u.id]?'Seguindo':u.private?'Solicitar':'Seguir'}</button><button class="secondary-btn" id="otherShare">${icon('share','icon-sm')}</button></div></section>
      ${restricted?`<section class="locked-profile"><div class="lock-orb">${icon('shield','icon-xl')}</div><h2>Este perfil é privado</h2><p>Siga ${u.name.split(' ')[0]} para visualizar as atividades e a biblioteca compartilhada.</p></section>`:`<div class="section-row"><h2>Atividade compartilhada</h2><span class="muted tiny">Somente informações que ${u.name.split(' ')[0]} tornou públicas</span></div><div class="activity-grid"><section class="activity-card"><h3>Atividade recente</h3>${[['Começou a ler','A Máquina do Tempo','hoje'],['Curtiu um trecho de','Drácula','ontem'],['Terminou','O Jardim Secreto','há 5 dias']].map(a=>`<div class="activity-item"><div class="activity-dot"></div><div><p><strong>${a[0]}</strong> ${a[1]}</p><span>${a[2]}</span></div></div>`).join('')}</section><section class="activity-card"><h3>Interesses públicos</h3><div class="taste-row"><span class="taste">Fantasia</span><span class="taste">Sci-fi</span><span class="taste">Aventura</span></div><div class="profile-stats"><div><strong>7</strong><span>dias de streak</span></div><div><strong>3</strong><span>livros no mês</span></div></div></section></div><div class="section-row"><h2>Favoritos públicos</h2></div><div class="book-grid">${[BOOKS[1],BOOKS[5],BOOKS[8],BOOKS[7],BOOKS[0]].map(bookCard).join('')}</div>`}</main>`;
    shell(content,u.name);
    document.getElementById('otherFollow').onclick=()=>{state.followed[u.id]=!state.followed[u.id];save();renderOtherProfile();toast(state.followed[u.id]?(u.private?'Solicitação enviada':'Agora você segue este leitor'):'Você deixou de seguir este leitor')};
    document.getElementById('otherShare').onclick=()=>openShare('profile');
    document.querySelectorAll('[data-open-book]').forEach(b=>b.onclick=()=>openBook(b.dataset.openBook));
  }

  function renderAdmin(){
    const tabs=[['overview','Visão geral'],['users','Usuários'],['moderation','Moderação']];
    const tabbar=`<div class="tab-row admin-tabs">${tabs.map(([id,l])=>`<button class="tab-btn ${state.adminTab===id?'active':''}" data-admin-tab="${id}">${l}</button>`).join('')}</div>`;
    let body='';
    if(state.adminTab==='overview') body=`<div class="dashboard-grid">${[['Usuários ativos','24,8 mil','+8,2%'],['Leituras iniciadas','81,4 mil','+12,6%'],['Comentários','9,2 mil','+4,1%'],['Denúncias abertas','18','−21%']].map(([l,v,c])=>`<article class="metric-card"><div class="metric-label"><span>${l}</span>${icon('chart','icon-sm')}</div><strong>${v}</strong><span class="metric-change">${c} nos últimos 30 dias</span></article>`).join('')}</div>
      <div class="dashboard-panels"><section class="panel-card"><div class="section-row" style="margin:0"><h2>Atividade da plataforma</h2><span class="muted tiny">Últimos 7 dias</span></div>${chartMarkup()}</section><section class="panel-card"><h3>Distribuição de ações</h3><div class="donut"><div class="donut-center"><div><strong>184k</strong><span>ações</span></div></div></div><div class="legend"><span style="--dot:var(--cyan)">Leituras</span><span style="--dot:var(--violet)">Curtidas</span><span style="--dot:var(--pink)">Salvos</span><span style="--dot:#323640">Outras</span></div></section></div>`;
    if(state.adminTab==='users') body=`<div class="page-heading compact-heading"><div><h2>Gerenciamento de usuários</h2><p>Visualize status e aplique ações de moderação mockadas.</p></div><div class="search-box mini-search">${icon('search','icon-sm')}<input id="adminUserSearch" placeholder="Buscar usuário..." /></div></div><div class="table-wrap"><table class="data-table"><thead><tr><th>Usuário</th><th>Status</th><th>Livros</th><th>Seguidores</th><th>Último acesso</th><th>Ações</th></tr></thead><tbody id="adminUsersBody">${adminUserRows()}</tbody></table></div>`;
    if(state.adminTab==='moderation') body=`<div class="page-heading compact-heading"><div><h2>Fila de moderação</h2><p>Conteúdos denunciados pelos leitores para revisão.</p></div><span class="pill">${3-Object.keys(state.removedReports||{}).length} pendentes</span></div><div class="moderation-list">${moderationCards()}</div>`;
    const content=`<main class="content"><div class="page-heading"><div><div class="eyebrow">OPERAÇÃO DA REDE</div><h1>Administração</h1><p>Visão geral da comunidade, usuários e conteúdo denunciado.</p></div><button class="secondary-btn" id="exportAdmin">${icon('share','icon-sm')} Exportar relatório</button></div>${tabbar}${body}</main>`;
    shell(content,'Administração');
    document.querySelectorAll('[data-admin-tab]').forEach(b=>b.onclick=()=>{state.adminTab=b.dataset.adminTab;save();renderAdmin()});
    document.getElementById('exportAdmin').onclick=()=>toast('Relatório mockado exportado com sucesso');
    const input=document.getElementById('adminUserSearch'); if(input) input.oninput=()=>{document.getElementById('adminUsersBody').innerHTML=adminUserRows(input.value)};
    bindAdminActions();
  }

  function chartMarkup(){
    return `<div class="chart-area"><svg class="chart-svg" viewBox="0 0 680 240" preserveAspectRatio="none"><defs><linearGradient id="chartGradient" x1="0" y1="0" x2="1" y2="0"><stop offset="0" stop-color="#25e0d4"/><stop offset="1" stop-color="#8d6cff"/></linearGradient><linearGradient id="chartFill" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#25e0d4"/><stop offset="1" stop-color="#25e0d4" stop-opacity="0"/></linearGradient></defs>${[40,90,140,190].map(y=>`<line class="chart-gridline" x1="0" y1="${y}" x2="680" y2="${y}"/>`).join('')}<path class="chart-fill" d="M0,185 C80,172 105,115 170,132 S265,78 340,92 S447,48 510,71 S605,46 680,35 L680,240 L0,240 Z"/><path class="chart-line" d="M0,185 C80,172 105,115 170,132 S265,78 340,92 S447,48 510,71 S605,46 680,35"/></svg></div><div class="chart-labels"><span>Seg</span><span>Ter</span><span>Qua</span><span>Qui</span><span>Sex</span><span>Sáb</span><span>Dom</span></div>`;
  }

  function adminUserRows(q=''){
    const list=[...USERS,{id:'u4',name:'Maya Luz',handle:'@mayaluz',initials:'ML',books:'42',followers:'1,9 mil'},{id:'u5',name:'Davi Alves',handle:'@davilê',initials:'DA',books:'73',followers:'5,2 mil'}].filter(u=>(u.name+' '+u.handle).toLowerCase().includes(q.toLowerCase()));
    return list.map((u,i)=>`<tr><td><div class="row-user"><div class="avatar">${u.initials}</div><div><strong>${u.name}</strong><div class="muted tiny">${u.handle}</div></div></div></td><td><span class="status ${state.blocked[u.id]?'bad':'ok'}">${state.blocked[u.id]?'Bloqueado':'Ativo'}</span></td><td>${u.books||57}</td><td>${u.followers||'—'}</td><td>${['Agora','12 min','1 h','Ontem','3 dias'][i%5]}</td><td><div class="table-actions"><button class="mini-btn" data-inspect-user="${u.id}">Detalhes</button><button class="mini-btn" data-block-user="${u.id}">${state.blocked[u.id]?'Desbloquear':'Bloquear'}</button></div></td></tr>`).join('');
  }

  function bindAdminActions(){
    document.querySelectorAll('[data-block-user]').forEach(b=>b.onclick=()=>{state.blocked[b.dataset.blockUser]=!state.blocked[b.dataset.blockUser];save();renderAdmin();toast(state.blocked[b.dataset.blockUser]?'Usuário bloqueado':'Usuário desbloqueado')});
    document.querySelectorAll('[data-inspect-user]').forEach(b=>b.onclick=()=>{const u=USERS.find(x=>x.id===b.dataset.inspectUser)||USERS[0];modal(`<div class="eyebrow">DETALHES DO USUÁRIO</div><h2>${u.name}</h2><p>${u.handle} • Conta criada há 11 meses</p><div class="profile-stats"><div><strong>${u.books||57}</strong><span>livros</span></div><div><strong>${u.followers||'—'}</strong><span>seguidores</span></div><div><strong>0</strong><span>strikes</span></div></div>`,`<button class="secondary-btn" data-close-modal>Fechar</button>`); bindModalClose();});
    document.querySelectorAll('[data-report-action]').forEach(b=>b.onclick=()=>{const [act,id]=b.dataset.reportAction.split(':'); if(act==='remove')state.removedReports[id]=true; save();toast(act==='remove'?'Conteúdo removido':'Denúncia arquivada');renderAdmin();});
  }

  function moderationCards(){
    const reports=[['r1','Comentário ofensivo','Comentário em “Frankenstein”','“Isso é ridículo, quem gosta disso...”','Linguagem inadequada'],['r2','Spam','Perfil @clubedolivro24','“Clique no link do meu perfil para ganhar...”','Conteúdo promocional repetitivo'],['r3','Trecho incorreto','A Máquina do Tempo','“Texto reportado como atribuído ao capítulo errado.”','Informação incorreta']];
    const visible=reports.filter(r=>!state.removedReports[r[0]]); if(!visible.length)return `<div class="empty-state"><div class="lock-orb">✓</div><h2>Fila limpa</h2><p>Não há denúncias pendentes neste mock.</p></div>`;
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
    return `<div class="publisher-layout"><section class="form-card"><h3>Dados editoriais</h3><form id="publishForm" class="form-grid"><div class="field"><label>Título</label><input id="pubTitle" required value="A Cidade Depois da Chuva" /></div><div class="field"><label>Autor</label><input id="pubAuthor" required value="Marina Duarte" /></div><div class="field"><label>Idioma</label><select><option>Português</option><option>Inglês</option><option>Espanhol</option></select></div><div class="field"><label>Categoria</label><select id="pubGenre"><option>Ficção</option><option>Romance</option><option>Fantasia</option><option>Ensaio</option></select></div><div class="field full"><label>Descrição</label><textarea>Uma narrativa sobre memória, cidade e recomeços, apresentada aqui apenas como conteúdo de demonstração do fluxo de publicação.</textarea></div><div class="field full"><label>Arquivo do livro</label><label class="dropzone"><input type="file" id="bookFile" hidden accept=".epub,.pdf"/><div>${icon('upload','icon-xl')}<strong>Arraste EPUB/PDF ou clique para selecionar</strong><span id="bookFileLabel">Máximo sugerido no protótipo: 100 MB</span></div></label></div><div class="field full"><label>Capa</label><label class="dropzone compact-drop"><input type="file" id="coverFile" hidden accept="image/*"/><div>${icon('upload')}<strong>Selecionar imagem de capa</strong><span id="coverFileLabel">JPG ou PNG</span></div></label></div><div class="field full"><div class="form-actions"><button type="button" class="secondary-btn" id="saveDraft">Salvar rascunho</button><button type="submit" class="primary-btn">Publicar livro ${icon('arrow','icon-sm')}</button></div></div></form></section><aside class="publish-preview"><div class="eyebrow">PRÉVIA</div><div class="book-cover" style="--cover-a:#7987ff;--cover-b:#33254f;background:linear-gradient(145deg,#7987ff,#33254f)"><span class="cover-mark">NOVA PUBLICAÇÃO</span><span class="cover-title" id="previewTitle">A Cidade Depois da Chuva</span></div><h3 id="previewAuthor">Marina Duarte</h3><p>Visualização aproximada no catálogo e recomendações.</p><div class="preview-status"><span class="status warn">Rascunho</span><span class="muted tiny">Metadados 80% completos</span></div></aside></div>`;
  }

  function publisherAnalytics(){
    const rows=[...state.published,{id:'p2',title:'Manual das Pequenas Descobertas',author:'Editora Horizonte',views:76300,reads:21100,likes:9800,shares:1850,a:'#56c7b4',b:'#17362f'},{id:'p3',title:'Entre Ruas e Estrelas',author:'Editora Horizonte',views:45200,reads:12900,likes:6300,shares:980,a:'#f0a25f',b:'#5a2833'}];
    return `<div class="dashboard-grid">${[['Visualizações','249,9 mil','+14,8%'],['Leituras iniciadas','68,2 mil','+9,3%'],['Curtidas','34,5 mil','+17,1%'],['Compartilhamentos','6,7 mil','+5,6%']].map(([l,v,c])=>`<article class="metric-card"><div class="metric-label"><span>${l}</span>${icon('chart','icon-sm')}</div><strong>${v}</strong><span class="metric-change">${c} vs. período anterior</span></article>`).join('')}</div><div class="dashboard-panels"><section class="panel-card"><div class="section-row" style="margin:0"><h2>Descoberta → leitura</h2><span class="muted tiny">Últimos 30 dias</span></div>${chartMarkup()}</section><section class="panel-card"><h3>Conversão média</h3><div class="funnel-list"><div><span>Impressões no feed</span><strong>100%</strong></div><div><span>Abertura do livro</span><strong>27,3%</strong></div><div><span>Leu 25%+</span><strong>15,8%</strong></div><div><span>Concluiu</span><strong>6,4%</strong></div></div></section></div><div class="section-row"><h2>Por publicação</h2><button class="secondary-btn" id="downloadAnalytics">Exportar CSV</button></div><div class="panel-card analytics-list">${rows.map(b=>`<div class="analytics-book-row"><div class="result-cover" style="background:linear-gradient(145deg,${b.a},${b.b})"></div><div><strong>${b.title}</strong><span>${b.author}</span></div><div class="analytics-number">${compact(b.views)}<small>visualizações</small></div><div class="analytics-number">${compact(b.reads)}<small>leituras</small></div><div class="analytics-number">${compact(b.likes)}<small>curtidas</small></div><div class="analytics-number">${compact(b.shares)}<small>shares</small></div></div>`).join('')}</div>`;
  }

  function bindPublisher(){
    const title=document.getElementById('pubTitle'), author=document.getElementById('pubAuthor');
    title?.addEventListener('input',()=>document.getElementById('previewTitle').textContent=title.value||'Sem título');
    author?.addEventListener('input',()=>document.getElementById('previewAuthor').textContent=author.value||'Autor');
    document.getElementById('bookFile')?.addEventListener('change',e=>{document.getElementById('bookFileLabel').textContent=e.target.files[0]?.name||'Nenhum arquivo selecionado'});
    document.getElementById('coverFile')?.addEventListener('change',e=>{document.getElementById('coverFileLabel').textContent=e.target.files[0]?.name||'Nenhuma imagem selecionada'});
    document.getElementById('saveDraft')?.addEventListener('click',()=>toast('Rascunho salvo localmente'));
    document.getElementById('publishForm')?.addEventListener('submit',e=>{e.preventDefault();const t=title.value.trim(),a=author.value.trim();state.published.unshift({id:'p'+Date.now(),title:t,author:a,views:0,reads:0,likes:0,shares:0,a:'#7987ff',b:'#33254f'});state.publisherTab='analytics';save();toast('Livro publicado no protótipo');renderPublisher()});
    document.getElementById('downloadAnalytics')?.addEventListener('click',()=>toast('CSV mockado gerado'));
  }

  function modal(body,actions=''){
    document.querySelector('.modal-wrap')?.remove();
    const wrap=document.createElement('div');wrap.className='modal-wrap';wrap.innerHTML=`<section class="modal">${body}<div class="modal-actions">${actions||'<button class="primary-btn" data-close-modal>Fechar</button>'}</div></section>`;document.body.appendChild(wrap);wrap.addEventListener('click',e=>{if(e.target===wrap)wrap.remove()});bindModalClose();
  }
  function bindModalClose(){document.querySelectorAll('[data-close-modal]').forEach(b=>b.onclick=()=>document.querySelector('.modal-wrap')?.remove())}

  function openComments(id){
    const b=book(id); const overlay=document.createElement('div');overlay.className='overlay';overlay.innerHTML=`<aside class="drawer"><div class="drawer-head"><div><h3>Comentários</h3><span class="muted tiny">${b.title}</span></div><button class="icon-btn" data-close-drawer>${icon('close')}</button></div><div class="drawer-body">${COMMENTS.map(c=>`<div class="comment"><div class="avatar">${c.initials}</div><div><strong>${c.name}</strong> <span>${c.time}</span><p>${c.text}</p></div></div>`).join('')}</div><div class="comment-compose"><input id="commentInput" placeholder="Escreva um comentário..."/><button class="primary-btn" id="commentSend">Enviar</button></div></aside>`;document.body.appendChild(overlay);
    overlay.querySelector('[data-close-drawer]').onclick=()=>overlay.remove(); overlay.addEventListener('click',e=>{if(e.target===overlay)overlay.remove()}); overlay.querySelector('#commentSend').onclick=()=>{const i=overlay.querySelector('#commentInput');if(!i.value.trim())return;toast('Comentário publicado no mock');i.value=''};
  }

  function openShare(id){
    const label=id==='profile'?'este perfil':`“${book(id).title}”`;
    modal(`<div class="eyebrow">COMPARTILHAR</div><h2>Envie ${label}</h2><p>As opções abaixo apenas simulam o comportamento de compartilhamento.</p><div class="share-grid"><button class="share-choice" data-share-choice><strong>↗</strong>Mensagem</button><button class="share-choice" data-share-choice><strong>◎</strong>Stories</button><button class="share-choice" data-share-choice><strong>◫</strong>Copiar link</button><button class="share-choice" data-share-choice><strong>•••</strong>Mais</button></div>`,`<button class="secondary-btn" data-close-modal>Cancelar</button>`);
    document.querySelectorAll('[data-share-choice]').forEach(b=>b.onclick=()=>{document.querySelector('.modal-wrap')?.remove();toast('Ação de compartilhamento simulada')});
  }

  function addMobileReaderTools(){
    if(mode!=='app'||state.route!=='reader')return;
    const main=document.querySelector('.reader-shell'); if(!main)return;
    const bar=document.createElement('div');bar.className='mobile-reader-tools';bar.innerHTML=`<button id="mobileBack">${icon('chevron','icon-sm')}</button><button id="mobileBookmark">${icon('bookmark','icon-sm')}</button><button id="mobileTheme">${icon('sun','icon-sm')}</button><button id="mobileFont">${icon('type','icon-sm')} A+</button>`;main.appendChild(bar);
    bar.querySelector('#mobileBack').onclick=()=>go('library');bar.querySelector('#mobileBookmark').onclick=()=>{save();toast(`Página marcada em ${state.readerPage}%`)};bar.querySelector('#mobileTheme').onclick=()=>{state.readerNight=!state.readerNight;save();renderReader();addMobileReaderTools()};bar.querySelector('#mobileFont').onclick=()=>{state.readerSize=state.readerSize>=24?18:state.readerSize+2;save();renderReader();addMobileReaderTools()};
  }

  const originalRenderReader=renderReader;
  renderReader=function(){originalRenderReader();addMobileReaderTools();};

  window.addEventListener('keydown',e=>{if((e.metaKey||e.ctrlKey)&&e.key.toLowerCase()==='k'){e.preventDefault();go('search')}});
  render();
})();
