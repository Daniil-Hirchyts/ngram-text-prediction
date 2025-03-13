package models

case class Probabilites[T](donnees: Map[T, Double] = Map.empty):
  /** Les n éléments les plus probables ; les égalités sont départagées par
    * ordre alphabétique pour que le résultat soit stable.
    */
  def plusProbables(n: Int): List[(T, Double)] =
    if n <= 0 then
      throw IllegalArgumentException("Le nombre d'éléments doit être positif")
    donnees.toList.sortBy((cle, p) => (-p, cle.toString)).take(n)

object Probabilites:
  /** Ramène des scores positifs à une distribution dont la somme vaut 1. */
  def normalisees[T](scores: Map[T, Double]): Probabilites[T] =
    val total = scores.values.sum
    if total <= 0 then Probabilites(Map.empty)
    else Probabilites(scores.map((cle, score) => (cle, score / total)))
