document.addEventListener('DOMContentLoaded', () => {
    const input = document.getElementById('autocomplete-input');
    const texteFantome = document.getElementById('autocomplete-text');
    const suggestionFantome = document.getElementById('autocomplete-suggestion');
    const fantome = document.querySelector('.autocomplete-wrapper');
    const tailleNGram = document.getElementById('autocomplete-taille-ngram');
    if (!input || !texteFantome || !suggestionFantome || !fantome || !tailleNGram) return;

    let completion = ''; // Le texte que Tab insérera.
    let requeteEnCours = null;

    function afficher() {
        texteFantome.textContent = input.value;
        suggestionFantome.textContent = completion;
        fantome.scrollTop = input.scrollTop;
    }

    function effacerSuggestion() {
        completion = '';
        afficher();
    }

    function curseurEnFin() {
        return input.selectionStart === input.value.length &&
            input.selectionEnd === input.value.length;
    }

    // Texte à ajouter après ce que l'utilisateur a tapé pour obtenir le mot proposé.
    function calculerCompletion(data, texte) {
        if (data.suggestions.length === 0) return '';
        const mot = data.suggestions[0].mot;
        if (data.prefixe) return mot.slice(data.prefixe.length);
        return (/\s$/.test(texte) ? '' : ' ') + mot;
    }

    async function demanderSuggestion() {
        // Seule la réponse à la dernière frappe compte : la précédente est annulée.
        if (requeteEnCours) requeteEnCours.abort();

        const texte = input.value;
        if (!texte.trim() || !curseurEnFin()) {
            effacerSuggestion();
            return;
        }

        const requete = new AbortController();
        requeteEnCours = requete;
        const params = new URLSearchParams({
            texte: finDuTexte(texte),
            taille: tailleNGram.value,
            nombre: '1',
            modelId: currentModelId
        });

        try {
            const data = await appelerApi(`/api/completion?${params}`, { signal: requete.signal });
            completion = calculerCompletion(data, texte);
            afficher();
        } catch (error) {
            if (error.name === 'AbortError') return;
            console.error('Erreur lors de la récupération de la suggestion:', error);
            effacerSuggestion();
        }
    }

    const demanderApresPause = debounce(demanderSuggestion, 100);

    function accepterSuggestion() {
        input.value += `${completion} `;
        input.selectionStart = input.selectionEnd = input.value.length;
        completion = '';
        afficher();
        demanderApresPause.cancel();
        demanderSuggestion();
    }

    input.addEventListener('input', () => {
        if (requeteEnCours) requeteEnCours.abort();
        effacerSuggestion();
        demanderApresPause();
    });

    input.addEventListener('keydown', (e) => {
        // Sans suggestion, Tab garde son rôle de navigation au clavier.
        if (e.key === 'Tab' && !e.shiftKey && completion) {
            e.preventDefault();
            accepterSuggestion();
        }
    });

    input.addEventListener('scroll', () => {
        fantome.scrollTop = input.scrollTop;
    });

    // Déplacer le curseur ailleurs qu'en fin de texte rend la suggestion caduque.
    const verifierCurseur = () => {
        if (!curseurEnFin()) {
            if (requeteEnCours) requeteEnCours.abort();
            effacerSuggestion();
        }
    };
    input.addEventListener('click', verifierCurseur);
    input.addEventListener('keyup', verifierCurseur);

    tailleNGram.addEventListener('change', demanderSuggestion);
    document.addEventListener('modelChanged', demanderSuggestion);
});
