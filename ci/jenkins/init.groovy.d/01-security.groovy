// Crée le compte admin et active la sécurité au premier démarrage.
// Credentials : admin / admin123
import jenkins.model.*
import hudson.security.*

def instance = Jenkins.get()

if (!(instance.getSecurityRealm() instanceof HudsonPrivateSecurityRealm)) {
    def realm = new HudsonPrivateSecurityRealm(false)
    realm.createAccount('admin', 'admin123')
    instance.setSecurityRealm(realm)

    def strategy = new FullControlOnceLoggedInAuthorizationStrategy()
    strategy.setAllowAnonymousRead(false)
    instance.setAuthorizationStrategy(strategy)

    instance.save()
    println '[INIT] Compte admin créé — login: admin / admin123'
} else {
    println '[INIT] Sécurité déjà configurée, skip.'
}