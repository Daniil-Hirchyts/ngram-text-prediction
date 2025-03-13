package models

/** Passage des comptages bruts (ce qui est stocké) au modèle interrogeable. */
object ModeleNGramme:
  val TailleMax = 3

  /** contexte -> mot suivant -> nombre d'occurrences */
  type ParContexte = Map[String, Map[String, Int]]

  /** taille du contexte -> comptages */
  type Comptages = Map[Int, ParContexte]

  def compter(texte: String): Comptages =
    (1 to TailleMax).map { taille =>
      val comptages = AnalyseurTexte(taille).compterNGrammes(texte)
      (taille, comptages.map((nGramme, mots) => (nGramme.mkString(" "), mots)))
    }.toMap

  /** Chaque texte est compté séparément : aucun n-gramme ne chevauche la fin
    * d'un fichier et le début du suivant.
    */
  def compterTextes(textes: Seq[String]): Comptages =
    textes.map(compter).foldLeft(Map.empty: Comptages)(fusionner)

  def fusionner(a: Comptages, b: Comptages): Comptages =
    (a.keySet ++ b.keySet).map { taille =>
      (
        taille,
        fusionnerContextes(
          a.getOrElse(taille, Map.empty),
          b.getOrElse(taille, Map.empty)
        )
      )
    }.toMap

  private def fusionnerContextes(a: ParContexte, b: ParContexte): ParContexte =
    b.foldLeft(a) { case (acc, (contexte, mots)) =>
      val existants = acc.getOrElse(contexte, Map.empty)
      val fusionnes = mots.foldLeft(existants) { case (m, (mot, n)) =>
        m.updated(mot, m.getOrElse(mot, 0) + n)
      }
      acc.updated(contexte, fusionnes)
    }

  /** Construit la chaîne de repli : 3 mots de contexte, puis 2, puis 1. */
  def construire(comptages: Comptages): Map[Int, SuggestionNGramme] =
    val vocabulaire = construireVocabulaire(comptages.getOrElse(1, Map.empty))
    (1 to TailleMax).foldLeft(Map.empty[Int, SuggestionNGramme]) {
      (modeles, taille) =>
        val probabilites = comptages
          .getOrElse(taille, Map.empty)
          .map((contexte, mots) =>
            (contexte, AnalyseurTexte.versProbabilites(mots))
          )
        val trie = ConstructeurTrie().construire(probabilites)
        modeles.updated(
          taille,
          SuggestionNGramme(trie, taille, modeles.get(taille - 1), vocabulaire)
        )
    }

  private def construireVocabulaire(unigrammes: ParContexte): Trie[String] =
    val frequences = unigrammes.values.flatten
      .groupMapReduce(_._1)(_._2)(_ + _)
    val total = frequences.values.sum.toDouble
    val entrees =
      frequences.map((mot, n) => (mot, Probabilites(Map(mot -> n / total))))
    ConstructeurTrie().construire(entrees)
