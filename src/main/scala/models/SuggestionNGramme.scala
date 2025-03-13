package models

/** Mots proposés pour un mot en cours de frappe : `prefixe` est la partie déjà
  * tapée, vide lorsqu'on prédit un mot entièrement nouveau.
  */
case class Completion(prefixe: String, suggestions: List[(String, Double)])

/** Prédit le mot suivant à partir des `tailleNGramme` derniers mots.
  *
  * @param repli
  *   modèle au contexte plus court, utilisé quand le contexte est inconnu
  * @param vocabulaire
  *   fréquence globale des mots, pour compléter un mot en cours de frappe
  */
class SuggestionNGramme(
    val trie: Trie[String],
    val tailleNGramme: Int,
    val repli: Option[SuggestionNGramme] = None,
    val vocabulaire: Trie[String] = Trie[String]()
):
  import SuggestionNGramme.Remise

  def extraireContexte(phrase: String): String =
    Tokeniseur.tokeniser(phrase).takeRight(tailleNGramme).mkString(" ")

  private def distributionExacte(
      mots: List[String]
  ): Option[Probabilites[String]] =
    if mots.length < tailleNGramme then None
    else trie.trouver(mots.takeRight(tailleNGramme).mkString(" "))

  /** Distribution du contexte le plus long connu. Le générateur y tire son mot
    * suivant : elle reste fidèle au corpus, sans bruit des contextes courts.
    */
  def distributionSuivante(mots: List[String]): Option[Probabilites[String]] =
    distributionExacte(mots).orElse(repli.flatMap(_.distributionSuivante(mots)))

  /** "Stupid backoff" : un mot garde le score du contexte le plus long où il
    * apparaît, et perd un facteur `Remise` à chaque mot de contexte en moins.
    */
  private def scores(mots: List[String], remise: Double): Map[String, Double] =
    val ici = distributionExacte(mots)
      .map(_.donnees.map((mot, p) => (mot, p * remise)))
      .getOrElse(Map.empty)
    val plusCourts =
      repli.map(_.scores(mots, remise * Remise)).getOrElse(Map.empty)
    plusCourts ++ ici

  private def suggererMots(mots: List[String], n: Int): List[(String, Double)] =
    Probabilites.normalisees(scores(mots, 1.0)).plusProbables(n)

  def suggerer(phrase: String, n: Int = 3): List[(String, Double)] =
    suggererMots(Tokeniseur.tokeniser(phrase), n)

  /** Comme `suggerer`, mais si la phrase se termine au milieu d'un mot, propose
    * des fins pour ce mot : d'abord selon le contexte, puis le vocabulaire.
    */
  def completer(phrase: String, n: Int = 3): Completion =
    val (mots, partiel) = Tokeniseur.separerMotPartiel(phrase)
    if partiel.isEmpty then Completion("", suggererMots(mots, n))
    else
      val selonContexte = scores(mots, 1.0).filter((mot, _) =>
        mot.startsWith(partiel) && mot != partiel
      )
      val remiseVocabulaire = math.pow(Remise, tailleNGramme.toDouble)
      val selonVocabulaire = vocabulaire
        .clesAvecPrefixe(partiel)
        .filter((mot, _) => mot != partiel)
        .flatMap((_, probs) => probs.donnees.toList)
        .map((mot, frequence) => (mot, frequence * remiseVocabulaire))
        .toMap
      val candidats = selonVocabulaire ++ selonContexte
      Completion(partiel, Probabilites.normalisees(candidats).plusProbables(n))

object SuggestionNGramme:
  /** Valeur proposée par Brants et al. (2007) pour le stupid backoff. */
  val Remise = 0.4
