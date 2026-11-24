// Run in the authenticated Jenkins Script Console when no build is active.
// Changes only the existing deploy job's SCM definition; preserves parameters,
// credentials, build history and jenkins_home. Does not trigger a deployment.
import jenkins.model.Jenkins
import hudson.plugins.git.GitSCM
import hudson.plugins.git.BranchSpec
import hudson.plugins.git.UserRemoteConfig
import org.jenkinsci.plugins.workflow.cps.CpsScmFlowDefinition

def job = Jenkins.get().getItemByFullName('bookrush-deploy')
assert job != null : 'bookrush-deploy must already exist'
assert !job.isBuilding() : 'Wait for the active build to finish'
def current = job.getDefinition()
def remotes = current instanceof CpsScmFlowDefinition && current.getScm() instanceof GitSCM
    ? current.getScm().getUserRemoteConfigs()
    : [new UserRemoteConfig('https://github.com/jvteodoro/BookRush.git', null, null, null)]
def scm = new GitSCM(remotes, [new BranchSpec('*/${BRANCH}')], false, [], null, null, [])
def definition = new CpsScmFlowDefinition(scm, 'Jenkinsfile')
definition.setLightweight(false)
job.setDefinition(definition)
job.save()
println('bookrush-deploy reconciled: Jenkinsfile from BRANCH; no build started')
