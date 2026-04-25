// Configure JDK-17 et Maven-3.9 pointant sur les binaires installés dans l'image.
// Ces noms doivent correspondre exactement aux blocs tools{} du Jenkinsfile.
import jenkins.model.*
import hudson.model.*
import hudson.tools.*

def instance = Jenkins.get()
def desc = instance.getDescriptor('hudson.model.JDK')

// --- JDK-17 ---
def jdkInstallations = desc.getInstallations()
if (!jdkInstallations.any { it.name == 'JDK-17' }) {
    def jdk = new JDK('JDK-17', '/opt/java/openjdk', [])
    desc.setInstallations(*jdkInstallations, jdk)
    desc.save()
    println '[INIT] JDK-17 configuré → /opt/java/openjdk'
}

// --- Maven-3.9 ---
def mavenDesc = instance.getDescriptor('hudson.tasks.Maven')
def mavenInstallations = mavenDesc.getInstallations()
if (!mavenInstallations.any { it.name == 'Maven-3.9' }) {
    def mvn = new hudson.tasks.Maven.MavenInstallation('Maven-3.9', '/opt/maven', [])
    mavenDesc.setInstallations(*mavenInstallations, mvn)
    mavenDesc.save()
    println '[INIT] Maven-3.9 configuré → /opt/maven'
}

instance.save()