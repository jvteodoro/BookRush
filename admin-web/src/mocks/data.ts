import { AnalyticsRun, BookCandidate, FeedItem, FeedWeights } from '../types/domain';

export const books: BookCandidate[] = [
  { id:'g-1342', source:'GUTENBERG', title:'Pride and Prejudice', author:'Jane Austen', language:'en', license:'Public domain', words:122189, status:'AVAILABLE', description:'Romance, irony and social observation.' },
  { id:'g-84', source:'GUTENBERG', title:'Frankenstein', author:'Mary Shelley', language:'en', license:'Public domain', words:78347, status:'IMPORTED', description:'Science, ambition and responsibility.' },
  { id:'g-2701', source:'GUTENBERG', title:'Moby-Dick', author:'Herman Melville', language:'en', license:'Public domain', words:209117, status:'IMPORTED', description:'Obsession, sea and metaphysical adventure.' },
  { id:'g-11', source:'GUTENBERG', title:"Alice's Adventures in Wonderland", author:'Lewis Carroll', language:'en', license:'Public domain', words:26432, status:'AVAILABLE', description:'Absurdity, logic and fantasy.' },
  { id:'g-1661', source:'GUTENBERG', title:'The Adventures of Sherlock Holmes', author:'Arthur Conan Doyle', language:'en', license:'Public domain', words:107536, status:'AVAILABLE', description:'Short-form detective fiction.' },
];

export const defaultWeights: FeedWeights = { hook:0.28, novelty:0.16, affinity:0.22, readability:0.12, diversity:0.10, exploration:0.12 };

export const feedItems: FeedItem[] = [
  { excerptId:'ex-001', bookId:'g-84', bookTitle:'Frankenstein', author:'Mary Shelley', text:'Life and death appeared to me ideal bounds, which I should first break through...', rankScore:0.93, features:{hook:0.96, novelty:0.82, affinity:0.90, readability:0.74} },
  { excerptId:'ex-002', bookId:'g-2701', bookTitle:'Moby-Dick', author:'Herman Melville', text:'Whenever I find myself growing grim about the mouth; whenever it is a damp, drizzly November in my soul...', rankScore:0.88, features:{hook:0.92, novelty:0.76, affinity:0.81, readability:0.67} },
  { excerptId:'ex-003', bookId:'g-1342', bookTitle:'Pride and Prejudice', author:'Jane Austen', text:'There is, I believe, in every disposition a tendency to some particular evil...', rankScore:0.83, features:{hook:0.84, novelty:0.71, affinity:0.78, readability:0.88} },
];

export const analytics: AnalyticsRun[] = [
  { id:'run-101', bookId:'g-84', status:'SUCCEEDED', modelVersion:'analytics-0.4.0', startedAt:'2026-09-29T10:12:00Z', finishedAt:'2026-09-29T10:13:11Z', metrics:[
    {key:'hook_density',label:'Hook density',value:0.78},
    {key:'lexical_diversity',label:'Lexical diversity',value:0.71},
    {key:'avg_sentence_words',label:'Avg. sentence',value:22.8,unit:'words'},
    {key:'excerpt_candidates',label:'Candidate excerpts',value:147}
  ]}
];
