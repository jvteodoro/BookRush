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
def pipelineScript = pipelineFile.text
if (!(job.getDefinition() instanceof CpsFlowDefinition) || job.getDefinition().getScript() != pipelineScript) {
    job.setDefinition(new CpsFlowDefinition(pipelineScript, true))
    job.save()
}
if (job.getProperty(ParametersDefinitionProperty.class) == null) {
    job.addProperty(new ParametersDefinitionProperty([
        new StringParameterDefinition('BRANCH', 'main', 'Branch a compilar e implantar.'),
        new BooleanParameterDefinition('DEPLOY', false, 'Atualizar a aplicação após o build.'),
        new ChoiceParameterDefinition('DEPLOY_TARGET', ['compose', 'kubernetes'] as String[], 'Destino.'),
        new StringParameterDefinition('GIT_CREDENTIAL_ID', '', 'Credential Git HTTPS opcional.'),
        new StringParameterDefinition('REGISTRY', '', 'Registry opcional para Compose.'),
        new StringParameterDefinition('REGISTRY_CREDENTIAL_ID', 'docker-registry', 'Credential do registry.'),
        new StringParameterDefinition('KUBECONFIG_CREDENTIAL_ID', 'bookrush-kubeconfig', 'Kubeconfig.')
    ]))
    job.save()
}
