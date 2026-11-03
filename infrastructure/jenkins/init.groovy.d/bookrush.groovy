import jenkins.model.Jenkins
import jenkins.model.JenkinsLocationConfiguration
import hudson.model.*
import org.jenkinsci.plugins.workflow.job.WorkflowJob
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition

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
def adminWebScript = adminWebPipelineFile.text
if (!(adminWebJob.getDefinition() instanceof CpsFlowDefinition) || adminWebJob.getDefinition().getScript() != adminWebScript) {
    adminWebJob.setDefinition(new CpsFlowDefinition(adminWebScript, true))
    adminWebJob.save()
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
    def script = file.text
    if (!(pipelineJob.getDefinition() instanceof CpsFlowDefinition) || pipelineJob.getDefinition().getScript() != script) {
        pipelineJob.setDefinition(new CpsFlowDefinition(script, true))
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
def backstageScript = backstagePipelineFile.text
if (!(backstageJob.getDefinition() instanceof CpsFlowDefinition) || backstageJob.getDefinition().getScript() != backstageScript) {
    backstageJob.setDefinition(new CpsFlowDefinition(backstageScript, true))
    backstageJob.save()
}
def pipelineScript = pipelineFile.text
if (!(job.getDefinition() instanceof CpsFlowDefinition) || job.getDefinition().getScript() != pipelineScript) {
    job.setDefinition(new CpsFlowDefinition(pipelineScript, true))
    job.save()
}
def mainParameters = [
    new StringParameterDefinition('BRANCH', 'main', 'Branch a compilar e implantar.'),
    new ChoiceParameterDefinition('DEPLOY_TARGET', ['compose', 'kubernetes'] as String[], 'Destino do deploy.'),
    new StringParameterDefinition('GIT_CREDENTIAL_ID', '', 'Credential Git HTTPS opcional.'),
    new StringParameterDefinition('REGISTRY', '', 'Registry sem barra final. Vazio: não publica imagens.'),
    new StringParameterDefinition('REGISTRY_CREDENTIAL_ID', 'docker-registry', 'Credential Jenkins do registry.'),
    new BooleanParameterDefinition('DEPLOY', false, 'Atualizar a aplicação no destino escolhido após o build.'),
    new StringParameterDefinition('KUBECONFIG_CREDENTIAL_ID', 'bookrush-kubeconfig', 'Credential Jenkins do tipo Secret file.'),
    new BooleanParameterDefinition('RUN_GUTENBERG_IMPORT', false, 'Executar explicitamente a importação Gutenberg.'),
    new BooleanParameterDefinition('GUTENBERG_DRY_RUN', true, 'Executar a importação Gutenberg em modo dry-run.'),
    new StringParameterDefinition('GUTENBERG_EXTERNAL_IDS', '1342', 'IDs Gutenberg separados por vírgula.'),
    new StringParameterDefinition('INGESTION_BASE_URL', 'https://bookrush.jteodoro.tec.br/ingestion', 'Base externa do gateway para o ingestion-service.'),
    new StringParameterDefinition('INGESTION_CREDENTIAL_ID', 'bookrush-ingestion-token', 'Credential Jenkins Secret text do operador.'),
    new ChoiceParameterDefinition('GUTENBERG_TIMEOUT_MINUTES', ['5', '15', '30'] as String[], 'Tempo máximo de polling do job Gutenberg.')
]
replaceParameters(job, mainParameters)
