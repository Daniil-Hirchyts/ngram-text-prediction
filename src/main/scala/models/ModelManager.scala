package models

import java.nio.file.{Files, Path}
import java.util.concurrent.atomic.AtomicReference
import org.slf4j.LoggerFactory
import services.FileService

enum ErreurModele:
  case IdentifiantInvalide(id: String)
  case Inconnu(id: String)
  case DejaExistant(id: String)
  case TexteInsuffisant
  case Protege(id: String)

case class ModelInfo(id: String, name: String)

/** Modèles disponibles. Les lectures passent par une photo immuable, jamais
  * bloquée ; les écritures sont sérialisées et n'échangent la photo qu'une fois
  * le nouveau modèle sauvegardé et prêt.
  */
final class ModelManager(
    depot: DepotModeles,
    dossierCorpus: Option[Path] = None
):
  import ModelManager.*

  private case class ModeleCharge(
      info: ModelInfo,
      suggestionneurs: Map[Int, SuggestionNGramme]
  )

  private val log = LoggerFactory.getLogger(getClass)
  private val modeles = AtomicReference(Map.empty[String, ModeleCharge])

  /** Recharge les modèles sauvegardés. La toute première fois, base vide, les
    * modèles initiaux sont appris : un modèle supprimé ne revient donc pas.
    */
  def initialiser(): Unit = synchronized {
    if depot.lister().isEmpty then amorcer()
    val charges = depot.lister().map { (id, nom) =>
      log.info("Chargement du modèle {}", id)
      (id, charger(id, nom))
    }
    modeles.set(charges.toMap)
  }

  private def charger(id: String, nom: String): ModeleCharge =
    ModeleCharge(
      ModelInfo(id, nom),
      ModeleNGramme.construire(depot.charger(id))
    )

  private def amorcer(): Unit =
    val corpus = CorpusInitiaux.flatMap { (dossier, id, nom) =>
      dossierCorpus
        .map(_.resolve(dossier))
        .filter(Files.isDirectory(_))
        .map(chemin => (id, nom, FileService.lireDossier(chemin)))
        .filter(_._3.nonEmpty)
    }
    val depart =
      if corpus.exists(_._1 == IdDefaut) then corpus
      else
        log.warn(
          "Corpus d'entraînement introuvable : modèle par défaut minimal"
        )
        (IdDefaut, NomDefaut, List(TexteMinimal)) :: corpus
    depart.foreach { (id, nom, textes) =>
      log.info("Apprentissage du modèle initial {}", id)
      depot.ajouter(id, nom, ModeleNGramme.compterTextes(textes))
    }

  def lister(): List[ModelInfo] =
    modeles.get.values
      .map(_.info)
      .toList
      .sortBy(info => (info.id != IdDefaut, info.id))

  def suggestionneur(id: String, taille: Int): Option[SuggestionNGramme] =
    modeles.get.get(id).flatMap(_.suggestionneurs.get(taille))

  /** Crée le modèle, ou l'enrichit si `ajouter` est vrai. Créer un modèle dont
    * le nom existe déjà est refusé : un upload n'efface jamais un modèle.
    */
  def entrainer(
      id: String,
      textes: Seq[String],
      ajouter: Boolean
  ): Either[ErreurModele, Unit] =
    if !identifiantValide(id) then Left(ErreurModele.IdentifiantInvalide(id))
    else
      val comptages = ModeleNGramme.compterTextes(textes)
      if comptages.values.forall(_.isEmpty) then
        Left(ErreurModele.TexteInsuffisant)
      else
        synchronized {
          val existant = modeles.get.get(id)
          (existant, ajouter) match
            case (None, true)     => Left(ErreurModele.Inconnu(id))
            case (Some(_), false) => Left(ErreurModele.DejaExistant(id))
            case _                =>
              val nom = existant.map(_.info.name).getOrElse(s"Modèle $id")
              depot.ajouter(id, nom, comptages)
              val charge = charger(id, nom)
              modeles.updateAndGet(_.updated(id, charge))
              Right(())
        }

  def supprimer(id: String): Either[ErreurModele, Unit] =
    if id == IdDefaut then Left(ErreurModele.Protege(id))
    else
      synchronized {
        if depot.supprimer(id) then
          modeles.updateAndGet(_ - id)
          Right(())
        else Left(ErreurModele.Inconnu(id))
      }

object ModelManager:
  val IdDefaut = "default"
  private val NomDefaut = "Modèle par défaut (anglais)"

  private val CorpusInitiaux = List(
    ("en", IdDefaut, NomDefaut),
    ("fr", "francais", "Modèle français")
  )

  private val IdentifiantAutorise = """[\p{L}\p{N}][\p{L}\p{N} _\-]{0,63}""".r

  def identifiantValide(id: String): Boolean =
    id != null && IdentifiantAutorise.matches(id)

  private val TexteMinimal =
    "Alice was beginning to get very tired of sitting by her sister on the bank, and of having nothing to do: once or twice she had peeped into the book her sister was reading, but it had no pictures or conversations in it."
