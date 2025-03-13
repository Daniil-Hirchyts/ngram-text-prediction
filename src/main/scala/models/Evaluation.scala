package models

/** Mesure la qualité de prédiction sur des textes que le modèle n'a pas vus. */
object Evaluation:
  /** @param top3
    *   part des mots réels présents parmi les 3 suggestions
    * @param couverture
    *   part des positions pour lesquelles au moins une suggestion existe
    */
  case class Resultat(top3: Double, couverture: Double)

  /** Sépare les mots : 90 % pour l'apprentissage, 10 % pour le test. */
  def separer(textes: Seq[String]): (List[String], List[String]) =
    val mots = textes.flatMap(Tokeniseur.tokeniser).toList
    mots.splitAt((mots.length * 0.9).toInt)

  def evaluer(
      suggestionneur: SuggestionNGramme,
      test: List[String]
  ): Resultat =
    val taille = suggestionneur.tailleNGramme
    val positions =
      test.sliding(taille + 1).filter(_.length == taille + 1).toList
    val reponses = positions.map { fenetre =>
      val proposes =
        suggestionneur.suggerer(fenetre.init.mkString(" "), 3).map(_._1)
      (proposes.nonEmpty, proposes.contains(fenetre.last))
    }
    val total = positions.length.max(1).toDouble
    Resultat(
      reponses.count(_._2) / total,
      reponses.count(_._1) / total
    )

  /** Modèles d'une taille donnée : contexte exact seul, et avec repli. */
  def modeles(
      entrainement: List[String]
  ): Map[Int, (SuggestionNGramme, SuggestionNGramme)] =
    val avecRepli = ModeleNGramme.construire(
      ModeleNGramme.compter(entrainement.mkString(" "))
    )
    avecRepli.map { (taille, modele) =>
      (taille, (SuggestionNGramme(modele.trie, taille), modele))
    }
