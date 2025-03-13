import models.{AnalyseurTexte, ConstructeurTrie, SuggestionNGramme}

@main def PropositionApp(): Unit =
  val texteExemple =
    "Alice was beginning to get very tired of sitting by her sister on the bank, and of having nothing to do:  once or twice she had peeped into the book her sister was reading, but it had no pictures or conversations in it, `and what is the use of a book,' thought Alice `without pictures or conversation?'    So she was considering in her own mind (as well as she could, for the hot day made her feel very sleepy and stupid), whether the pleasure of making a daisy-chain would be worth the trouble of getting up and picking the daisies, when suddenly a White Rabbit with pink eyes ran close by her."

  val texte = texteExemple

  // Configuration
  val tailleNGramme =
    1

  // Analyse et construction
  val analyseur = AnalyseurTexte(tailleNGramme)
  val constructeur = ConstructeurTrie()
  val probabilites = analyseur.preparerPourTrie(texte)
  val trie = constructeur.construire(probabilites)

  // Création du suggestionneur
  val suggestionneur = SuggestionNGramme(trie, tailleNGramme)

  // Démarrer l'interface utilisateur
  demarrerInterfaceUtilisateur(suggestionneur, tailleNGramme)

// Fonction pour gérer l'interface utilisateur
def demarrerInterfaceUtilisateur(
    suggestionneur: SuggestionNGramme,
    tailleNGramme: Int
): Unit =
  // Interface utilisateur récursive
  def boucleInteraction(): Unit =
    println(
      s"Entrez exactement $tailleNGramme mot(s) pour obtenir des suggestions (ou 'q' pour quitter):"
    )
    val entreeUtilisateur = scala.io.StdIn.readLine()

    if entreeUtilisateur != null && entreeUtilisateur != "q" then
      traiterEntreeUtilisateur(entreeUtilisateur)
      boucleInteraction()

  def traiterEntreeUtilisateur(entree: String): Unit =
    val suggestions = suggestionneur.suggerer(entree, 5)

    if suggestions.isEmpty then
      println(
        s"Aucune suggestion trouvée. Assurez-vous d'entrer exactement $tailleNGramme mot(s)."
      )
    else
      println("Mots les plus probables:")
      suggestions.foreach { case (mot, prob) =>
        println(f"$mot (${prob * 100}%.1f%%)")
      }

  boucleInteraction()
