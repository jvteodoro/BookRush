# Java code style

O código Java de produção segue o Google Java Format aplicado pelo build local
e pelos serviços do monorepo. Classes, métodos, records, construtores e
consultas SQL devem usar blocos e quebras de linha legíveis; funções inteiras
em uma única linha não são aceitas.

Para formatar os arquivos Java sem alterar comportamento, use a versão fixada
do formatador adotada pela equipe e revise o diff:

```bash
java -jar google-java-format-all-deps.jar -i \
  $(find services platform -path '*/src/main/java/*.java' -type f)
git diff --check
```

Formatação é uma mudança separada de mudanças funcionais. Depois dela, rode
os testes Maven dos módulos modificados. Se um `target/` criado por Docker
estiver pertencendo a `root`, execute o teste em um checkout temporário sem o
diretório `target`; não altere permissões de dados do ambiente para contornar
o problema.
