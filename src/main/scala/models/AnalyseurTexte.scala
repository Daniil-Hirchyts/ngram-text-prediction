package models

class AnalyseurTexte(tailleNGramme: Int = 2):
  if tailleNGramme < 1 then
    throw IllegalArgumentException("La taille du n-gramme doit être au moins 1")

  /** Pour chaque suite de n mots, compte les mots qui la suivent. */
  def compterNGrammes(texte: String): Map[List[String], Map[String, Int]] =
    val mots = Tokeniseur.tokeniser(texte)
    if mots.length <= tailleNGramme then Map.empty
    else
      mots
        .sliding(tailleNGramme + 1)
        .toList
        .groupMap(_.init)(_.last)
        .map((nGramme, suivants) =>
          (nGramme, suivants.groupMapReduce(identity)(_ => 1)(_ + _))
        )

  def calculerProbabilites(
      comptages: Map[List[String], Map[String, Int]]
  ): Map[List[String], Probabilites[String]] =
    comptages.map((nGramme, mots) =>
      (nGramme, AnalyseurTexte.versProbabilites(mots))
    )

  def analyserTexte(texte: String): Map[List[String], Probabilites[String]] =
    calculerProbabilites(compterNGrammes(texte))

  /** Le trie est indexé par chaîne : "le chat" plutôt que List("le", "chat").
    */
  def convertirNGrammesEnCles(
      probsNGrammes: Map[List[String], Probabilites[String]]
  ): Map[String, Probabilites[String]] =
    probsNGrammes.map((nGramme, probs) => (nGramme.mkString(" "), probs))

  def preparerPourTrie(texte: String): Map[String, Probabilites[String]] =
    convertirNGrammesEnCles(analyserTexte(texte))

object AnalyseurTexte:
  def versProbabilites(comptages: Map[String, Int]): Probabilites[String] =
    Probabilites.normalisees(comptages.map((mot, n) => (mot, n.toDouble)))
