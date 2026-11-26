# Registro da decisão acadêmica sobre a política etária e de supervisão v1

**Decisão** Responsável pelo projeto acadêmico, em 24/11/2026, no pedido
de implementação. Política `age-supervision-v1`, exclusivamente para o
protótipo educacional com contas sintéticas e pagamentos simulados.

O responsável dispensou aprovações departamentais de Produto, Privacidade
e Jurídico para esta fase. Essas aprovações não foram obtidas. A decisão
acadêmica não constitui parecer jurídico, aprovação de comercialização
ou autorização para usar dados de menores reais em testes.

**Base** [Política proposta](README.md), [vetores de fronteira](fixtures.tsv)
e [contrato de autorização v1](../product-access-v1/README.md). As referências
legais estão na política. Jurídico deve registrar o enquadramento aplicável
e a versão das fontes que embasam seu parecer.

## Decisões consolidadas

| ID | Regra do protótipo |
|---|---|
| P1 | Adultos têm acesso gratuito. Menores autenticam e usam sessão mínima, convites próprios e logout. Produto geral exige membership ativa, Premium simulado vigente e PRODUCT_ACCESS. Supervisão autorizada segue disponível sem pagamento. Vencimento remove benefícios comerciais e preserva identidade, histórico e concessões legais. |
| P2 | Gratuito não publica. Titular Premium e familiares adultos devidamente delegados precisam de decisão atual e ownership. Menores não publicam. Aos 18, reavaliar direitos sem conceder delegação automaticamente. |
| J1 | Birthdate ISO obrigatório no registro, rejeitando datas inválidas ou futuras. Declaração separada da verificação. Verificação sintética posterior e restrição onde exigida. Sem documento, foto ou biometria reais. Correção controlada e auditável. |
| J2 | Vínculo comercial independente do grant responsável–menor. Aprovação, rejeição, revogação e consulta do grant sintético ficam na entrega posterior. A concessão revogada não revive com pagamento ou convite. |
| D1 | PRODUCT_ACCESS, BOOKS_ACCESSED, READING_ACTIVITY e COMMENTS independentes. Dados supervisionados posteriores à concessão, até 90 dias e limitados à sua vigência. Auditoria de acessos por 180 dias. Donos mantêm sua retenção autoritativa. Sem exportação em massa ou reexposição de comentários moderados. Revogação e maioridade encerram toda consulta parental. |
| J3 | Corte às 00h em America/Sao_Paulo aos 18 anos. Nascimento em 29/02 usa 01/03 quando necessário, por convenção do protótipo. Relógio confiável e fatos atuais, inclusive com token antigo. |
| P3 | Legado sem data é DATA_AUSENTE, preservando subject e autenticação. Preenchimento inicial idempotente e auditável; correções restritas. Gate transversal somente após bootstrap e rotas mínimas. |
| T1 | Contrato v1, decisões atuais e fail-closed, com 401/403/503 e exceções explícitas. Revogação e aniversário independem de novo token. Nenhum cache excede a vigência. |

A implantação comercial requer revisão própria de enquadramento, métodos
de verificação, responsabilidade legal, privacidade, retenção e modelo
de acesso. Os parâmetros acadêmicos não são regras legais de produção.
