package services

import java.nio.ByteBuffer
import java.nio.charset.{
  CharacterCodingException,
  Charset,
  CodingErrorAction,
  StandardCharsets
}
import java.nio.file.{Files, Path}
import scala.jdk.CollectionConverters.*
import scala.util.Using

object FileService:
  private val Windows1252 = Charset.forName("windows-1252")
  private val DebutGutenberg = """(?m)^\*\*\* ?START OF[^\n]*\n""".r
  private val FinGutenberg = """(?m)^\*\*\* ?END OF""".r

  def estFichierTexte(nom: String): Boolean =
    nom.toLowerCase.endsWith(".txt")

  /** Décode en UTF-8 ; à défaut, en Windows-1252, encodage courant des anciens
    * fichiers français, plutôt que de refuser le fichier.
    */
  def decoder(octets: Array[Byte]): String =
    val texte =
      try
        StandardCharsets.UTF_8
          .newDecoder()
          .onMalformedInput(CodingErrorAction.REPORT)
          .onUnmappableCharacter(CodingErrorAction.REPORT)
          .decode(ByteBuffer.wrap(octets))
          .toString
      catch case _: CharacterCodingException => new String(octets, Windows1252)
    texte.stripPrefix("﻿")

  /** Retire l'en-tête et la licence des livres du Projet Gutenberg, qui ne font
    * pas partie de l'œuvre et fausseraient les statistiques.
    */
  def retirerGutenberg(texte: String): String =
    val debut = DebutGutenberg.findFirstMatchIn(texte).map(_.end).getOrElse(0)
    val fin = FinGutenberg
      .findFirstMatchIn(texte)
      .map(_.start)
      .filter(_ >= debut)
      .getOrElse(texte.length)
    texte.substring(debut, fin)

  def preparer(octets: Array[Byte]): String =
    retirerGutenberg(decoder(octets).replace("\r\n", "\n"))

  def lireDossier(dossier: Path): List[String] =
    Using.resource(Files.list(dossier)) { fichiers =>
      fichiers.iterator.asScala
        .filter(f => estFichierTexte(f.getFileName.toString))
        .toList
        .sortBy(_.toString)
        .map(f => preparer(Files.readAllBytes(f)))
    }
