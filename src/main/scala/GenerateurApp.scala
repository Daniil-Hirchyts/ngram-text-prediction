import models.{
  AnalyseurTexte,
  ConstructeurTrie,
  SuggestionNGramme,
  GenerateurTexte
}

@main def GenerateurApp(): Unit =
  val texteExemple =
    "Alice was beginning to get very tired of sitting by her sister on the bank, and of having nothing to do:  once or twice she had peeped into the book her sister was reading, but it had no pictures or conversations in it, `and what is the use of a book,' thought Alice `without pictures or conversation?'    So she was considering in her own mind (as well as she could, for the hot day made her feel very sleepy and stupid), whether the pleasure of making a daisy-chain would be worth the trouble of getting up and picking the daisies, when suddenly a White Rabbit with pink eyes ran close by her. Alice had never before seen a rabbit with either a waistcoat-pocket, or a watch to take out of it. There was nothing so VERY remarkable in that; nor did Alice think it so VERY much out of the way to hear the Rabbit say to itself, `Oh dear! Oh dear! I shall be late!' But when the Rabbit actually TOOK A WATCH OUT OF ITS WAISTCOAT-POCKET, and looked at it, and then hurried on, Alice started to her feet."

  val texte = texteExemple

  // Taille du n-gramme (3 pour trigrammes)
  val tailleNGramme = 3

  // Analyse et construction
  val analyseur = AnalyseurTexte(tailleNGramme)
  val probabilites = analyseur.preparerPourTrie(texte)
  val trie = ConstructeurTrie().construire(probabilites)

  // Création du suggestionneur et du générateur
  val suggestionneur = SuggestionNGramme(trie, tailleNGramme)
  val generateur = GenerateurTexte(suggestionneur)

  // Générer et afficher le texte
  genererEtAfficherTexte(generateur, "alice was beginning", 50)

// Fonction séparée pour générer et afficher le texte
def genererEtAfficherTexte(
    generateur: GenerateurTexte,
    prefixe: String,
    nombreMots: Int
): Unit =
  val texteGenere = generateur.generer(prefixe, nombreMots)
  println(s"Texte généré à partir de '$prefixe':")
  println(texteGenere)
