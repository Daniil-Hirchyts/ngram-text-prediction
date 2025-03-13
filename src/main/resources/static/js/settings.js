document.addEventListener('DOMContentLoaded', () => {
    const fileInput = document.getElementById('file-upload');
    const uploadBtn = document.getElementById('upload-btn');
    const statut = document.getElementById('upload-status');
    const creerRadio = document.getElementById('create-new-model');
    const ajouterRadio = document.getElementById('add-to-existing');
    const nomInput = document.getElementById('new-model-name');
    const listeModeles = document.getElementById('models-list-container');
    const fichiersChoisis = document.getElementById('selected-files');
    if (!uploadBtn || !fileInput || !statut || !listeModeles) return;

    function afficherStatut(message, classe) {
        statut.textContent = message;
        statut.className = classe;
    }

    fileInput.addEventListener('change', () => {
        const n = fileInput.files.length;
        fichiersChoisis.textContent =
            n === 0 ? 'Aucun fichier sélectionné'
                : n === 1 ? `Fichier sélectionné : ${fileInput.files[0].name}`
                    : `${n} fichiers sélectionnés`;
    });

    function creerBouton(texte, classe, action) {
        const bouton = document.createElement('button');
        bouton.textContent = texte;
        bouton.classList.add(classe);
        bouton.addEventListener('click', action);
        return bouton;
    }

    function afficherListe() {
        listeModeles.replaceChildren(...modelesDisponibles.map(model => {
            const selectionne = model.id === currentModelId;
            const item = document.createElement('div');
            item.classList.add('model-item');
            item.classList.toggle('selected', selectionne);

            const nom = document.createElement('div');
            nom.classList.add('model-name');
            nom.textContent = model.name;
            if (selectionne) {
                const badge = document.createElement('span');
                badge.textContent = 'Sélectionné';
                badge.classList.add('current-model-tag');
                nom.appendChild(badge);
            }

            const actions = document.createElement('div');
            actions.classList.add('model-actions');
            if (!selectionne) {
                actions.appendChild(creerBouton('Sélectionner', 'select-btn',
                    () => selectionnerModele(model.id)));
            }
            if (model.id !== 'default') {
                actions.appendChild(creerBouton('Supprimer', 'delete-btn', () => {
                    if (confirm(`Êtes-vous sûr de vouloir supprimer le modèle "${model.name}" ?`)) {
                        supprimerModele(model);
                    }
                }));
            }

            item.append(nom, actions);
            return item;
        }));
    }

    async function supprimerModele(model) {
        try {
            await appelerApi(`/api/models/${encodeURIComponent(model.id)}`, { method: 'DELETE' });
            afficherStatut(`Modèle "${model.name}" supprimé.`, 'success');
            await loadModels();
        } catch (error) {
            console.error('Erreur lors de la suppression du modèle:', error);
            afficherStatut(error.message, 'error');
        }
    }

    uploadBtn.addEventListener('click', async () => {
        if (fileInput.files.length === 0) {
            afficherStatut('Veuillez sélectionner au moins un fichier.', 'error');
            return;
        }

        const creation = creerRadio.checked;
        const nouveauNom = nomInput.value.trim();
        if (creation && !nouveauNom) {
            afficherStatut('Veuillez donner un nom au nouveau modèle.', 'error');
            return;
        }

        const formData = new FormData();
        for (const file of fileInput.files) {
            formData.append('files', file);
        }
        const params = new URLSearchParams({
            modelId: creation ? nouveauNom : currentModelId,
            append: creation ? 'false' : 'true'
        });

        afficherStatut('Apprentissage en cours...', 'loading');
        uploadBtn.disabled = true;
        try {
            await appelerApi(`/api/upload?${params}`, { method: 'POST', body: formData });
            afficherStatut('Fichiers appris et modèle mis à jour.', 'success');

            if (creation) {
                currentModelId = nouveauNom;
                nomInput.value = '';
                ajouterRadio.checked = true;
                ajouterRadio.dispatchEvent(new Event('change'));
            }
            await loadModels();

            fileInput.value = '';
            fichiersChoisis.textContent = 'Aucun fichier sélectionné';
        } catch (error) {
            console.error("Erreur d'upload:", error);
            afficherStatut(`Erreur : ${error.message}`, 'error');
        } finally {
            uploadBtn.disabled = false;
        }
    });

    document.addEventListener('modelChanged', afficherListe);
});
