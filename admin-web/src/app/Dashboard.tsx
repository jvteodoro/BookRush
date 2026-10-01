import { useQuery } from '@tanstack/react-query';
import { ArrowUpRight, BarChart3, BookOpen, FlaskConical } from 'lucide-react';
import { Link } from 'react-router-dom';
import { services } from '../lib/services';

export function Dashboard(){
 const books=useQuery({queryKey:['books'],queryFn:()=>services.listBooks()}); const runs=useQuery({queryKey:['analytics'],queryFn:()=>services.listAnalytics()});
 const imported=books.data?.filter(b=>b.status==='IMPORTED').length??0;
 return <section><header className="pagehead hero"><div><span className="eyebrow">LABSOFT ADMINISTRATIVE PLATFORM</span><h1>Operate content, analytics and recommendation training.</h1><p>Uma console única para construir o corpus, executar modelos e gerar os primeiros sinais comportamentais do algoritmo.</p></div></header>
 <div className="stats"><div><BookOpen/><span>Imported books</span><strong>{imported}</strong></div><div><BarChart3/><span>Analytics runs</span><strong>{runs.data?.length??0}</strong></div><div><FlaskConical/><span>Training mode</span><strong>Ready</strong></div></div>
 <div className="quick"><Link to="/catalog"><span>01</span><div><strong>Curate corpus</strong><p>Escolha e importe livros públicos.</p></div><ArrowUpRight/></Link><Link to="/analytics"><span>02</span><div><strong>Run analytics</strong><p>Produza features e trechos candidatos.</p></div><ArrowUpRight/></Link><Link to="/feed-lab"><span>03</span><div><strong>Train the feed</strong><p>Role, reaja e ajuste pesos em tempo real.</p></div><ArrowUpRight/></Link></div>
 </section>
}
