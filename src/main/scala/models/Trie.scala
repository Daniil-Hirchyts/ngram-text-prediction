package models

import java.util.Locale

case class Trie[T](
    enfants: Map[Char, Trie[T]] = Map.empty[Char, Trie[T]],
    valeur: Option[Probabilites[T]] = None
):
  def inserer(mot: String, probs: Probabilites[T]): Trie[T] =
    if mot == null || mot.trim.isEmpty then
      throw IllegalArgumentException("Le mot ne peut pas être null ou vide")
    if probs == null then
      throw IllegalArgumentException(
        "Les probabilités ne peuvent pas être null"
      )

    insererAide(Trie.normaliserCle(mot), 0, probs)

  private def insererAide(
      mot: String,
      index: Int,
      probs: Probabilites[T]
  ): Trie[T] =
    if index == mot.length then Trie(enfants, Some(probs))
    else
      val caractere = mot(index)
      val enfantActuel = enfants.getOrElse(caractere, Trie[T]())
      val enfantMisAJour = enfantActuel.insererAide(mot, index + 1, probs)
      Trie(enfants + (caractere -> enfantMisAJour), valeur)

  def trouver(prefixe: String): Option[Probabilites[T]] =
    if prefixe == null then None
    else noeud(Trie.normaliserCle(prefixe)).flatMap(_.valeur)

  private def noeud(chemin: String): Option[Trie[T]] =
    chemin.foldLeft(Option(this)) { (courant, caractere) =>
      courant.flatMap(_.enfants.get(caractere))
    }

  /** Toutes les clés commençant par le préfixe, avec leur valeur. C'est la
    * recherche qui justifie un arbre plutôt qu'une table de hachage.
    */
  def clesAvecPrefixe(prefixe: String): List[(String, Probabilites[T])] =
    if prefixe == null then Nil
    else
      val depart = Trie.normaliserCle(prefixe)
      noeud(depart).map(_.parcourir(depart)).getOrElse(Nil)

  private def parcourir(cle: String): List[(String, Probabilites[T])] =
    valeur.map(v => (cle, v)).toList ++ enfants.toList.flatMap {
      (caractere, enfant) => enfant.parcourir(cle + caractere)
    }

  def obtenirPlusProbables(prefixe: String, n: Int = 3): List[(T, Double)] =
    trouver(prefixe) match
      case Some(probs) => probs.plusProbables(n)
      case None        => List.empty

object Trie:
  private def normaliserCle(cle: String): String =
    cle.toLowerCase(Locale.ROOT).trim
