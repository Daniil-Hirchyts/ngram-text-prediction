document.addEventListener('DOMContentLoaded', () => {
    const texteInput = document.getElementById('texte-input');
    const tailleNGram = document.getElementById('taille-ngram');
    const conteneur = document.querySelector('.suggestions');
    if (!texteInput || !tailleNGram || !conteneur) return;

    let requeteEnCours = null;

    function afficherMessage(message) {
        const p = document.createElement('p');
        p.textContent = message;
        conteneur.replaceChildren(p);
    }

    function afficherSuggestions(suggestions) {
        conteneur.replaceChildren(...suggestions.map(suggestion => {
            const element = document.createElement('div');
            element.classList.add('suggestion-item');
            element.dataset.mot = suggestion.mot;
            element.textContent = `${suggestion.mot} `;

            const pourcentage = document.createElement('span');
            pourcentage.classList.add('probability');
            pourcentage.textContent = `${(suggestion.probabilite * 100).toFixed(1)}%`;
            element.appendChild(pourcentage);
            return element;
        }));
    }

    async function actualiser() {
        if (requeteEnCours) requeteEnCours.abort();

        const texte = texteInput.value.trim();
        if (!texte) {
            afficherMessage('Les suggestions apparaîtront ici');
            return;
        }

        const requete = new AbortController();
        requeteEnCours = requete;
        const params = new URLSearchParams({
            texte: finDuTexte(texte),
            taille: tailleNGram.value,
            modelId: currentModelId
        });

        try {
            const data = await appelerApi(`/api/suggestions?${params}`, { signal: requete.signal });
            if (data.suggestions.length > 0) {
                afficherSuggestions(data.suggestions);
            } else {
                afficherMessage('Aucune suggestion trouvée');
            }
        } catch (error) {
            if (error.name === 'AbortError') return;
            console.error('Erreur lors de la récupération des suggestions:', error);
            afficherMessage(`Erreur : ${error.message}`);
        }
    }

    texteInput.addEventListener('input', debounce(actualiser, 300));
    tailleNGram.addEventListener('change', actualiser);
    document.addEventListener('modelChanged', actualiser);

    conteneur.addEventListener('click', (e) => {
        const element = e.target.closest('.suggestion-item');
        if (!element) return;
        texteInput.value = `${texteInput.value.trimEnd()} ${element.dataset.mot}`;
        texteInput.focus();
        actualiser();
    });
});
