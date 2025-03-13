package models

import java.text.Normalizer
import java.util.Locale

/** Découpe un texte en mots. Entraînement et prédiction passent par le même
  * code : un mot appris est donc toujours retrouvé à l'identique.
  */
object Tokeniseur:
  private val Mot = """[\p{L}\p{M}]+(?:['\-][\p{L}\p{M}]+)*""".r
  private val MotPartiel = """[\p{L}\p{M}][\p{L}\p{M}'\-]*$""".r

  def normaliser(texte: String): String =
    Normalizer
      .normalize(texte, Normalizer.Form.NFC)
      .replace('’', '\'')
      .toLowerCase(Locale.ROOT)

  def tokeniser(texte: String): List[String] =
    if texte == null then Nil
    else Mot.findAllIn(normaliser(texte)).toList

  /** Sépare les mots terminés du mot en cours de frappe : "the que" donne
    * (List("the"), "que") et "the que " donne (List("the", "que"), "").
    */
  def separerMotPartiel(texte: String): (List[String], String) =
    if texte == null then (Nil, "")
    else
      val normalise = normaliser(texte)
      MotPartiel.findFirstIn(normalise) match
        case Some(partiel) =>
          val debut = normalise.dropRight(partiel.length)
          (tokeniser(debut), partiel)
        case None => (tokeniser(normalise), "")
