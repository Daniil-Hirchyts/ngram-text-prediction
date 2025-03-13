package models

import scala.annotation.tailrec
import scala.util.Random

class GenerateurTexte(
    val suggestionneur: SuggestionNGramme,
    val random: Random = new Random()
):
  /** Tire un mot au hasard, proportionnellement à sa probabilité. */
  private def tirerMot(distribution: Probabilites[String]): Option[String] =
    val candidats = distribution.donnees.toList
    val cible = random.nextDouble() * candidats.map(_._2).sum

    @tailrec
    def trouver(reste: List[(String, Double)], cumul: Double): Option[String] =
      reste match
        case Nil                  => None
        case (mot, prob) :: suite =>
          if cible <= cumul + prob then Some(mot)
          else trouver(suite, cumul + prob)

    trouver(candidats, 0.0).orElse(candidats.lastOption.map(_._1))

  /** Prolonge le préfixe. Seule une fenêtre des derniers mots est conservée :
    * le coût de chaque mot reste constant quelle que soit la longueur du texte.
    */
  def generer(prefixe: String, nombreMots: Int): String =
    if prefixe == null || prefixe.trim.isEmpty then ""
    else if nombreMots <= 0 then prefixe
    else
      val taille = suggestionneur.tailleNGramme

      @tailrec
      def boucle(
          contexte: List[String],
          generes: List[String],
          reste: Int
      ): List[String] =
        if reste <= 0 then generes.reverse
        else
          suggestionneur.distributionSuivante(contexte).flatMap(tirerMot) match
            case Some(mot) =>
              boucle(
                (contexte :+ mot).takeRight(taille),
                mot :: generes,
                reste - 1
              )
            case None => generes.reverse

      val depart = Tokeniseur.tokeniser(prefixe).takeRight(taille)
      (prefixe.trim :: boucle(depart, Nil, nombreMots)).mkString(" ")
