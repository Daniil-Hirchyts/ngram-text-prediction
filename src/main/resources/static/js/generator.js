document.addEventListener('DOMContentLoaded', () => {
    const prefixeInput = document.getElementById('prefixe-input');
    const tailleNGram = document.getElementById('taille-ngram-gen');
    const nombreMots = document.getElementById('nombre-mots');
    const bouton = document.getElementById('generate-btn');
    const conteneur = document.querySelector('.generated-text');
    if (!prefixeInput || !tailleNGram || !nombreMots || !bouton || !conteneur) return;

    function afficher(message) {
        const p = document.createElement('p');
        p.textContent = message;
        conteneur.replaceChildren(p);
    }

    bouton.addEventListener('click', async () => {
        const prefixe = prefixeInput.value.trim();
        if (!prefixe) {
            afficher('Veuillez entrer un préfixe');
            return;
        }

        afficher('Génération en cours...');
        const params = new URLSearchParams({
            prefixe: finDuTexte(prefixe),
            tailleNGramme: tailleNGram.value,
            nombreMots: nombreMots.value,
            modelId: currentModelId
        });

        try {
            const data = await appelerApi(`/api/generer?${params}`);
            afficher(data.texte);
        } catch (error) {
            console.error('Erreur lors de la génération du texte:', error);
            afficher(`Erreur : ${error.message}`);
        }
    });
});
