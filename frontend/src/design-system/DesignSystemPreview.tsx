import { useState } from 'react';
import { Button } from '../components/ui/Button';
import { Card } from '../components/ui/Card';
import { Chip } from '../components/ui/Chip';
import { Logo } from '../components/ui/Logo';
import { ProgressBar } from '../components/ui/ProgressBar';
import { useTheme } from '../theme/ThemeProvider';
import { Switch } from '../components/ui/Switch';
import { CarouselControl } from '../components/ui/CarouselControl';

export function DesignSystemPreview() {
  const { theme, toggle } = useTheme();
  const [genre, setGenre] = useState('Suspense');
  const [progress, setProgress] = useState(35);
  const [sharing, setSharing] = useState(true);
  const [slide, setSlide] = useState(1);
  return <main className="br-preview"><div className="br-preview__content">
    <header className="br-preview__header"><Logo size={64} /><Button variant="ghost" onClick={toggle}>Ativar tema {theme === 'dark' ? 'claro' : 'escuro'}</Button></header>
    <h1>Design system BookRush</h1><p>Prévia dos componentes e temas. Dados demonstrativos.</p><a href="/home-preview">Ver a página inicial →</a>
    <div className="br-preview__grid">
      <Card><h2>Botões</h2><div className="br-preview__row"><Button>Continuar leitura</Button><Button variant="secondary" size="sm">Biblioteca</Button><Button variant="ghost">Ver mais</Button><Button disabled>Indisponível</Button></div></Card>
      <Card><h2>Filtros</h2><div className="br-preview__row">{['Suspense', 'Romance', 'Aventura'].map(item => <Chip key={item} active={genre === item} onClick={() => setGenre(item)}>{item}</Chip>)}<Chip disabled>Em breve</Chip></div></Card>
      <Card><h2>Liga e desliga</h2><Switch label="Compartilhar atividade" checked={sharing} onChange={event => setSharing(event.target.checked)} /><p>{sharing ? 'Ativado' : 'Desativado'}</p><Switch label="Opção indisponível" disabled /></Card>
      <Card><h2>Área de rolagem</h2><div className="br-scroll-area br-preview__reading" tabIndex={0} aria-label="Exemplo de texto rolável" style={{ maxHeight: 160, overflowY: 'auto', paddingRight: 12 }}>{Array.from({ length: 6 }, (_, index) => <p key={index}>Cada página abre uma nova história. Role para continuar a leitura no seu tempo.</p>)}</div></Card>
      <Card><h2>Controle de carrossel</h2><div className="br-preview__row"><CarouselControl direction="previous" label="Exemplo anterior" disabled={slide === 1} onClick={() => setSlide(current => current - 1)} /><span aria-live="polite">Exemplo {slide} de 3</span><CarouselControl direction="next" label="Próximo exemplo" disabled={slide === 3} onClick={() => setSlide(current => current + 1)} /></div></Card>
      <Card><h2>Progresso de leitura</h2><p id="preview-progress-label">Leitura concluída: {progress}%</p><ProgressBar value={progress} label="Progresso de leitura" /><p><input type="range" min="0" max="100" value={progress} aria-labelledby="preview-progress-label" onChange={event => setProgress(Number(event.target.value))} /></p></Card>
      <Card><h2>Tipografia e paleta</h2><p>Interface em Plus Jakarta Sans.</p><p className="br-preview__reading">Cada página abre uma nova história.</p><div className="br-preview__row">{['red', 'orange', 'gold', 'teal', 'teal-dark', 'cream', 'ink'].map(color => <span key={color} title={color} className="br-preview__swatch" style={{ background: `var(--br-${color})` }} />)}</div></Card>
    </div>
  </div></main>;
}
