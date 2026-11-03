import jenkins.model.Jenkins
import jenkins.model.JenkinsLocationConfiguration
import hudson.model.*
import org.jenkinsci.plugins.workflow.job.WorkflowJob
import org.jenkinsci.plugins.workflow.cps.CpsScmFlowDefinition
import hudson.plugins.git.GitSCM
import hudson.plugins.git.UserRemoteConfig
import hudson.plugins.git.BranchSpec

def instance = Jenkins.get()
JenkinsLocationConfiguration.get().setUrl('https://jenkins-bookrush.jteodoro.tec.br/')
def pipelineFile = new File('/opt/bookrush/bookrush.Jenkinsfile')
def job = instance.getItem('bookrush-deploy')
if (job == null) {
    job = instance.createProject(WorkflowJob, 'bookrush-deploy')
}

def backstagePipelineFile = new File('/opt/bookrush/backstage.Jenkinsfile')
def backstageJob = instance.getItem('bookrush-backstage')
if (backstageJob == null) {
    backstageJob = instance.createProject(WorkflowJob, 'bookrush-backstage')
}

def adminWebPipelineFile = new File('/opt/bookrush/admin-web.Jenkinsfile')
def adminWebJob = instance.getItem('bookrush-admin-web')
if (adminWebJob == null) {
    adminWebJob = instance.createProject(WorkflowJob, 'bookrush-admin-web')
}
def microservicePipelineFile = new File('/opt/bookrush/microservice.Jenkinsfile')
def verticalPipelineFile = new File('/opt/bookrush/vertical.Jenkinsfile')
def replaceParameters = { WorkflowJob pipelineJob, List definitions ->
    pipelineJob.removeProperty(ParametersDefinitionProperty.class)
    pipelineJob.addProperty(new ParametersDefinitionProperty(definitions))
    pipelineJob.save()
}
def microserviceParameters = [
    new StringParameterDefinition('BRANCH', 'main', 'Branch a validar.'),
    new StringParameterDefinition('SERVICE', '', 'Microserviço. Em jobs nomeados, o sufixo do job é usado quando vazio.'),
    new StringParameterDefinition('GIT_CREDENTIAL_ID', '', 'Credential Git opcional para repositório privado.'),
    new StringParameterDefinition('REGISTRY', '', 'Registry sem barra final. Vazio: não publica.'),
    new StringParameterDefinition('REGISTRY_CREDENTIAL_ID', 'docker-registry', 'Credential Jenkins do registry.'),
    new BooleanParameterDefinition('PUBLISH', false, 'Publicar a imagem validada.'),
    new ChoiceParameterDefinition('DEPLOY_TARGET', ['none', 'compose', 'kubernetes'] as String[], 'Destino do deploy; none apenas valida.'),
    new BooleanParameterDefinition('DEPLOY', false, 'Implantar a imagem validada no destino escolhido.'),
    new StringParameterDefinition('KUBECONFIG_CREDENTIAL_ID', 'bookrush-kubeconfig', 'Credential Jenkins do tipo Secret file para Kubernetes.')
]
def verticalParameters = [
    new StringParameterDefinition('BRANCH', 'main', 'Branch a validar.'),
    new StringParameterDefinition('VERTICAL', '', 'Vertical. Em jobs nomeados, o sufixo do job é usado quando vazio.'),
    new StringParameterDefinition('GIT_CREDENTIAL_ID', '', 'Credential Git opcional para repositório privado.'),
    new StringParameterDefinition('REGISTRY', '', 'Registry sem barra final. Obrigatório para publicar ou Kubernetes.'),
    new StringParameterDefinition('REGISTRY_CREDENTIAL_ID', 'docker-registry', 'Credential Jenkins do registry.'),
    new BooleanParameterDefinition('PUBLISH', false, 'Publicar todas as imagens validadas da vertical.'),
    new ChoiceParameterDefinition('DEPLOY_TARGET', ['none', 'compose', 'kubernetes'] as String[], 'Destino do deploy; none apenas valida.'),
    new BooleanParameterDefinition('DEPLOY', false, 'Implantar todas as imagens validadas da vertical.'),
    new StringParameterDefinition('KUBECONFIG_CREDENTIAL_ID', 'bookrush-kubeconfig', 'Credential Jenkins do tipo Secret file para Kubernetes.')
]
def createOrUpdatePipeline = { String name, File file ->
    def pipelineJob = instance.getItem(name)
    if (pipelineJob == null) {
        pipelineJob = instance.createProject(WorkflowJob, name)
    }
    def scm = new GitSCM(
        [new UserRemoteConfig('https://github.com/jvteodoro/BookRush.git', null, null, null)],
        [new BranchSpec('*/${BRANCH}')], false, [], null, null, [])
    def definition = new CpsScmFlowDefinition(scm, file.name)
    definition.setLightweight(false)
    if (!(pipelineJob.getDefinition() instanceof CpsScmFlowDefinition)) {
        pipelineJob.setDefinition(definition)
        pipelineJob.save()
    }
    replaceParameters(pipelineJob, file == microservicePipelineFile ? microserviceParameters : verticalParameters)
}
[
    'admin-service', 'behavior-service', 'book-content-service',
    'book-ingestion-service', 'book-analytics-service', 'catalog-service',
    'publisher-service', 'reader-bff-service', 'reader-profile-service',
    'reader-state-service', 'recommendation-service', 'social-service'
].each { service -> createOrUpdatePipeline("bookrush-microservice-${service}", microservicePipelineFile) }
['catalog', 'ingestion', 'reader', 'social', 'analytics', 'publishing', 'platform'].each { vertical ->
    createOrUpdatePipeline("bookrush-vertical-${vertical}", verticalPipelineFile)
}
def scmFor = { String path ->
    def scm = new GitSCM(
        [new UserRemoteConfig('https://github.com/jvteodoro/BookRush.git', null, null, null)],
        [new BranchSpec('*/${BRANCH}')], false, [], null, null, [])
    def definition = new CpsScmFlowDefinition(scm, path)
    definition.setLightweight(false)
    return definition
}
if (!(backstageJob.getDefinition() instanceof CpsScmFlowDefinition)) {
    backstageJob.setDefinition(scmFor(backstagePipelineFile.name))
    backstageJob.save()
}
if (!(adminWebJob.getDefinition() instanceof CpsScmFlowDefinition)) {
    adminWebJob.setDefinition(scmFor(adminWebPipelineFile.name))
    adminWebJob.save()
}
if (!(job.getDefinition() instanceof CpsScmFlowDefinition)) {
    job.setDefinition(scmFor('Jenkinsfile'))
    job.save()
}
def mainParameters = [
    new StringParameterDefinition('BRANCH', 'main', 'Branch a compilar e implantar.'),
    new ChoiceParameterDefinition('DEPLOY_TARGET', ['compose', 'kubernetes'] as String[], 'Destino do deploy.'),
    new StringParameterDefinition('GIT_CREDENTIAL_ID', '', 'Credential Git HTTPS opcional.'),
    new StringParameterDefinition('REGISTRY', '', 'Registry sem barra final. Vazio: não publica imagens.'),
    new StringParameterDefinition('REGISTRY_CREDENTIAL_ID', 'docker-registry', 'Credential Jenkins do registry.'),
    new BooleanParameterDefinition('DEPLOY', false, 'Atualizar a aplicação no destino escolhido após o build.'),
    new BooleanParameterDefinition('RUN_ANALYTICS_SAMPLE', false, 'Cancelar jobs analíticos ativos e processar uma amostra controlada de scores.'),
    new StringParameterDefinition('ANALYTICS_SAMPLE_MAX_BOOKS', '4', 'Quantidade máxima de livros no lote controlado de scores.'),
    new ChoiceParameterDefinition('FEED_PERSONALIZED_MODE', ['ON', 'OFF'] as String[], 'Ativar o feed personalizado; ON é o padrão durante a fase de testes.'),
    new StringParameterDefinition('KUBECONFIG_CREDENTIAL_ID', 'bookrush-kubeconfig', 'Credential Jenkins do tipo Secret file.'),
    new BooleanParameterDefinition('RUN_GUTENBERG_IMPORT', false, 'Executar explicitamente a importação Gutenberg.'),
    new BooleanParameterDefinition('GUTENBERG_DRY_RUN', true, 'Executar a importação Gutenberg em modo dry-run.'),
    new StringParameterDefinition('GUTENBERG_EXTERNAL_IDS', '1342', 'IDs Gutenberg separados por vírgula.'),
    new StringParameterDefinition('INGESTION_BASE_URL', 'https://bookrush.jteodoro.tec.br/ingestion', 'Base externa do gateway para o ingestion-service.'),
    new StringParameterDefinition('INGESTION_CREDENTIAL_ID', 'bookrush-ingestion-token', 'Credential Jenkins Secret text do operador.'),
    new ChoiceParameterDefinition('GUTENBERG_TIMEOUT_MINUTES', ['5', '15', '30'] as String[], 'Tempo máximo de polling do job Gutenberg.')
]
replaceParameters(job, mainParameters)
