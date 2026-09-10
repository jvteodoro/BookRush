# Jenkins

Jenkins é o componente de CI/CD da plataforma. A definição versionada da
pipeline está em [`infrastructure/jenkins/bookrush.Jenkinsfile`](https://github.com/jvteodoro/BookRush/blob/main/infrastructure/jenkins/bookrush.Jenkinsfile)
e o deploy executa a composição de produção de forma controlada.

O portal não substitui o Jenkins: ele apresenta o ownership, as dependências e
os procedimentos operacionais para que o time encontre o pipeline correto.
