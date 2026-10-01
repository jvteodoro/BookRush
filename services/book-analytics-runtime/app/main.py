import os
import hashlib
import math
import zipfile
from collections import Counter
from pathlib import Path
from fastapi import FastAPI, HTTPException
from pydantic import BaseModel, Field

CACHE=Path(os.getenv('ANALYTICS_MODEL_CACHE_DIR','/var/lib/bookrush/models'))
MAX_CHARS=int(os.getenv('ANALYTICS_RUNTIME_MAX_CHARS','100000'))
app=FastAPI(title='BookRush Analytics Runtime', version='v2')
_embedding_model = None
_embedding_tokenizer = None
_spacy_models = {}
_nli_pipeline = None
_language_model = None

class AnalyzeRequest(BaseModel):
 language: str
 text: str = Field(min_length=1)
class AnalyzeResponse(BaseModel):
 status: str
 language: str
 values: dict[str,float] = {}
 warning: str|None = None

class EmbeddingRequest(BaseModel):
 texts: list[str] = Field(min_length=1, max_length=32)
 max_tokens: int = Field(default=8192, ge=1, le=8192)

class EmbeddingResponse(BaseModel):
 status: str
 model: str
 dimension: int = 0
 vectors: list[list[float]] = []
 input_hashes: list[str] = []
 warning: str|None = None
class NliRequest(BaseModel):
 text: str = Field(min_length=1)
 hypothesis: str = Field(min_length=1)
class NliResponse(BaseModel):
 status: str
 entailment: float = 0.0
 neutral: float = 0.0
 contradiction: float = 0.0
 model: str = 'mdeberta-v3-mnli-xnli'
 warning: str|None = None

class LanguageRequest(BaseModel):
 text: str = Field(min_length=1)
class LanguageResponse(BaseModel):
 status: str
 language: str = 'und'
 confidence: float = 0.0
 model: str = 'fasttext-lid176-v1'
 warning: str|None = None

MODELS={'en':'spacy-en-core-web-sm-v1/en_core_web_sm-3.8.0-py3-none-any.whl','pt':'spacy-pt-core-news-sm-v1/pt_core_news_sm-3.8.0-py3-none-any.whl'}
def unavailable(language, reason): return AnalyzeResponse(status='MODEL_UNAVAILABLE',language=language,warning=reason)
@app.get('/health')
def health(): return {'status':'up','offline':os.getenv('ANALYTICS_OFFLINE','true').lower()=='true'}

def _load_embedding_model():
 global _embedding_model, _embedding_tokenizer
 if _embedding_model is not None: return _embedding_tokenizer, _embedding_model
 model_dir=CACHE/'bge-m3-dense-v1'
 if not model_dir.is_dir(): return None
 try:
  from transformers import AutoModel, AutoTokenizer
  _embedding_tokenizer=AutoTokenizer.from_pretrained(model_dir, local_files_only=True)
  _embedding_model=AutoModel.from_pretrained(model_dir, local_files_only=True)
  _embedding_model.eval()
  return _embedding_tokenizer, _embedding_model
 except Exception: return None

@app.post('/v1/embeddings', response_model=EmbeddingResponse)
def embeddings(request: EmbeddingRequest):
 loaded=_load_embedding_model()
 if loaded is None: return EmbeddingResponse(status='MODEL_UNAVAILABLE',model='bge-m3-dense-v1',warning='pinned BGE-M3 cache is not prepared')
 tokenizer, model=loaded
 try:
  import torch
  encoded=tokenizer(request.texts, padding=True, truncation=True, max_length=request.max_tokens, return_tensors='pt')
  with torch.inference_mode(): output=model(**encoded).last_hidden_state
  mask=encoded['attention_mask'].unsqueeze(-1)
  pooled=(output*mask).sum(1)/mask.sum(1).clamp(min=1)
  normalized=torch.nn.functional.normalize(pooled,p=2,dim=1)
  vectors=normalized.cpu().tolist()
  return EmbeddingResponse(status='VALID',model='bge-m3-dense-v1',dimension=len(vectors[0]),vectors=vectors,input_hashes=[hashlib.sha256(t.encode('utf-8')).hexdigest() for t in request.texts])
 except Exception as exc:
  return EmbeddingResponse(status='ERROR',model='bge-m3-dense-v1',warning=type(exc).__name__)

def _load_nli():
 global _nli_pipeline
 if _nli_pipeline is not None: return _nli_pipeline
 model_dir=CACHE/'mdeberta-nli-v1'
 if not model_dir.is_dir(): return None
 try:
  from transformers import pipeline
  _nli_pipeline=pipeline('text-classification',model=str(model_dir),tokenizer=str(model_dir),top_k=None)
  return _nli_pipeline
 except Exception: return None

def _load_language_model():
 global _language_model
 if _language_model is not None: return _language_model
 model_path=CACHE/'fasttext-lid176-v1'/'lid.176.bin'
 if not model_path.is_file(): return None
 try:
  import fasttext
  _language_model=fasttext.load_model(str(model_path))
  return _language_model
 except Exception: return None

@app.post('/v1/language', response_model=LanguageResponse)
def language(request: LanguageRequest):
 if len(request.text)>MAX_CHARS: raise HTTPException(413,'text exceeds configured runtime limit')
 model=_load_language_model()
 if model is None: return LanguageResponse(status='MODEL_UNAVAILABLE',warning='fastText lid.176 cache or runtime dependency is unavailable')
 try:
  clean=request.text.replace('\n',' ')
  labels, scores=model.predict([clean], k=1)
  if labels and isinstance(labels[0], list): labels, scores=labels[0], scores[0]
  label=labels[0].removeprefix('__label__') if labels else 'und'
  return LanguageResponse(status='VALID',language=label,confidence=float(scores[0]) if scores else 0.0)
 except Exception as exc:
  return LanguageResponse(status='ERROR',warning=type(exc).__name__)

@app.post('/v1/nli', response_model=NliResponse)
def nli(request: NliRequest):
 if len(request.text)>MAX_CHARS or len(request.hypothesis)>MAX_CHARS: raise HTTPException(413,'text exceeds configured runtime limit')
 model=_load_nli()
 if model is None: return NliResponse(status='MODEL_UNAVAILABLE',warning='pinned NLI cache is not prepared')
 try:
  result=model({'text':request.text, 'text_pair':request.hypothesis})
  items=result[0] if result and isinstance(result[0],list) else result
  scores={str(item['label']).lower():float(item['score']) for item in items if isinstance(item,dict)}
  def find(name): return next((v for k,v in scores.items() if name in k),0.0)
  return NliResponse(status='VALID',entailment=find('entail'),neutral=find('neutral'),contradiction=find('contrad'))
 except Exception as exc: return NliResponse(status='ERROR',warning=f'{type(exc).__name__}: {str(exc)[:180]}')
@app.post('/v1/linguistic', response_model=AnalyzeResponse)
def linguistic(request: AnalyzeRequest):
 if request.language not in MODELS: return AnalyzeResponse(status='UNSUPPORTED',language=request.language,warning='spaCy V2 supports only en and pt')
 if len(request.text)>MAX_CHARS: raise HTTPException(413,'text exceeds configured runtime limit')
 wheel=CACHE/MODELS[request.language]
 if not wheel.is_file(): return unavailable(request.language, f'missing prepared model artifact: {wheel.name}')
 try:
  import spacy
  if request.language not in _spacy_models:
   unpacked=Path('/tmp/bookrush-spacy') / request.language
   model_path=unpacked / ('en_core_web_sm' if request.language=='en' else 'pt_core_news_sm')
   if not model_path.is_dir():
    unpacked.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(wheel) as archive:
     root=unpacked.resolve()
     for member in archive.infolist():
      target=(unpacked / member.filename).resolve()
      if root != target and root not in target.parents: raise ValueError('unsafe model archive path')
     archive.extractall(unpacked)
   nested=[p for p in model_path.iterdir() if p.is_dir() and (p/'config.cfg').is_file()] if model_path.is_dir() else []
   if nested: model_path=nested[0]
   _spacy_models[request.language]=spacy.load(model_path)
  nlp=_spacy_models[request.language]
 except Exception as exc:
  return unavailable(request.language, f'prepared wheel is not installed in runtime: {type(exc).__name__}')
 try:
  doc=nlp(request.text)
  tokens=[t for t in doc if not t.is_space]
  if not tokens: return AnalyzeResponse(status='INSUFFICIENT_SAMPLE',language=request.language,warning='no tokens')
  total=len(tokens); pos=Counter(t.pos_ for t in tokens); entities=list(doc.ents)
  depths=[]; distances=[]; subordinate=0
  for t in tokens:
   seen=set(); cursor=t; depth=0
   while cursor.head!=cursor and cursor.i not in seen:
    seen.add(cursor.i); depth+=1; distances.append(abs(cursor.i-cursor.head.i)); cursor=cursor.head
   depths.append(depth)
   if t.dep_ in ('advcl','ccomp','xcomp','acl','relcl'): subordinate += 1
  ordered=sorted(depths)
  p90=ordered[min(len(ordered)-1, math.ceil(len(ordered)*.9)-1)]
  values={
   'unique_lemma_ratio':len({t.lemma_.lower() for t in tokens})/total,
   'noun_ratio':pos['NOUN']/total, 'verb_ratio':pos['VERB']/total,
   'adjective_ratio':pos['ADJ']/total,'adverb_ratio':pos['ADV']/total,
   'pronoun_ratio':pos['PRON']/total,'finite_verb_density':sum(t.pos_=='VERB' and 'Fin' in t.morph.get('VerbForm') for t in tokens)/total,
   'dependency_depth_mean':sum(depths)/len(depths),'dependency_depth_p90':float(p90),
   'dependency_distance_mean':sum(distances)/max(1,len(distances)),
   'entity_count':float(len(entities)),'unique_entity_count':float(len({e.text.lower() for e in entities})),
   'entity_density_per_100_words':100*len(entities)/total,
   'person_entity_density_per_100_words':100*sum(e.label_=='PERSON' for e in entities)/total,
   'subordinate_clause_ratio':subordinate/total,
   'opening_pronoun_ratio':sum(t.pos_=='PRON' for t in tokens[:min(20,total)])/min(20,total),
  }
  return AnalyzeResponse(status='VALID',language=request.language,values=values)
 except Exception as exc: return AnalyzeResponse(status='ERROR',language=request.language,warning=type(exc).__name__)
