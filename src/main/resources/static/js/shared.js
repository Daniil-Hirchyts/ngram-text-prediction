// État et utilitaires communs à toutes les pages.
let currentModelId = lireModeleSauvegarde() || 'default';
let modelesDisponibles = [];

// Le serveur n'a besoin que des derniers mots : envoyer seulement la fin du texte
// évite de dépasser la longueur maximale d'une URL sur les textes longs.
const LONGUEUR_CONTEXTE_MAX = 200;

function finDuTexte(texte) {
    return texte.slice(-LONGUEUR_CONTEXTE_MAX);
}

function lireModeleSauvegarde() {
    try {
        return localStorage.getItem('currentModelId');
    } catch {
        return null;
    }
}

function sauvegarderModele(id) {
    try {
        localStorage.setItem('currentModelId', id);
    } catch {
        // Stockage indisponible : la sélection vaut pour la page courante.
    }
}

// Appelle l'API et renvoie le JSON ; une réponse d'erreur lève son message.
async function appelerApi(url, options) {
    const response = await fetch(url, options);
    let data = null;
    try {
        data = await response.json();
    } catch {
        // Corps absent ou non JSON : traité ci-dessous comme une erreur générique.
    }
    if (!response.ok) {
        throw new Error((data && data.error) || `Erreur du serveur (${response.status})`);
    }
    return data;
}

function selectionnerModele(id) {
    currentModelId = id;
    sauvegarderModele(id);
    afficherModeleCourant();
    document.dispatchEvent(new CustomEvent('modelChanged', { detail: { modelId: id } }));
}

function afficherModeleCourant() {
    const modele = modelesDisponibles.find(m => m.id === currentModelId);
    const nomAffiche = document.getElementById('selected-model-name');
    if (nomAffiche && modele) {
        nomAffiche.textContent = modele.name;
    }
    const modelSelect = document.getElementById('model-select');
    if (modelSelect && modele) {
        modelSelect.value = modele.id;
    }
}

// Recharge la liste des modèles et s'assure que le modèle courant existe encore.
async function loadModels() {
    const modelSelect = document.getElementById('model-select');
    try {
        const data = await appelerApi('/api/models');
        modelesDisponibles = data.models;

        if (modelSelect) {
            modelSelect.replaceChildren(...modelesDisponibles.map(model => {
                const option = document.createElement('option');
                option.value = model.id;
                option.textContent = model.name;
                return option;
            }));
        }

        const existe = modelesDisponibles.some(m => m.id === currentModelId);
        selectionnerModele(existe ? currentModelId : 'default');
    } catch (error) {
        console.error('Erreur lors du chargement des modèles:', error);
    }
}

function initialiserFormulaireModele() {
    const creer = document.getElementById('create-new-model');
    const ajouter = document.getElementById('add-to-existing');
    const nom = document.getElementById('new-model-name');
    if (!creer || !ajouter || !nom) return;

    const actualiser = () => {
        nom.parentElement.style.display = creer.checked ? 'block' : 'none';
        const info = document.getElementById('existing-model-info');
        if (info) info.style.display = creer.checked ? 'none' : 'block';
    };
    creer.addEventListener('change', actualiser);
    ajouter.addEventListener('change', actualiser);
    actualiser();
}

function debounce(func, wait) {
    let timeout;
    const debounced = function (...args) {
        clearTimeout(timeout);
        timeout = setTimeout(() => func.apply(this, args), wait);
    };
    debounced.cancel = () => clearTimeout(timeout);
    return debounced;
}

document.addEventListener('DOMContentLoaded', () => {
    const modelSelect = document.getElementById('model-select');
    if (modelSelect) {
        modelSelect.addEventListener('change', () => selectionnerModele(modelSelect.value));
    }
    initialiserFormulaireModele();
    loadModels();
});
