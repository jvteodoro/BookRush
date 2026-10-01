import { NavLink, Outlet } from 'react-router-dom';
import { BarChart3, BookOpen, FlaskConical, LayoutDashboard, LogOut, ShieldCheck, GraduationCap } from 'lucide-react';
import { useAuth } from '../auth/useAuth';

const items = [
  {to:'/', label:'Overview', icon:LayoutDashboard},
  {to:'/catalog', label:'Catalog & Import', icon:BookOpen},
  {to:'/analytics', label:'Analytics', icon:BarChart3},
  {to:'/feed-lab', label:'Feed Lab', icon:FlaskConical},
  {to:'/training', label:'Training', icon:GraduationCap},
];

export function Layout(){
  const auth=useAuth();
  return <div className="shell">
    <aside className="sidebar">
      <div className="brand"><div className="brandmark">L</div><div><strong>LabSoft</strong><span>Admin Console</span></div></div>
      <nav>{items.map(({to,label,icon:Icon})=><NavLink key={to} to={to} end={to==='/'} className={({isActive})=>isActive?'active':''}><Icon size={18}/>{label}</NavLink>)}</nav>
      <div className="identity"><div className="avatar">{auth.username.slice(0,2).toUpperCase()}</div><div><strong>{auth.username}</strong><span><ShieldCheck size={12}/> {auth.roles.slice(0,2).join(' · ')}</span></div><button onClick={auth.logout}><LogOut size={16}/></button></div>
    </aside>
    <main className="main"><Outlet/></main>
  </div>
}
