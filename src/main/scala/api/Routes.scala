package api

import models.{
  ErreurModele,
  GenerateurTexte,
  ModelInfo,
  ModelManager,
  SuggestionNGramme
}
import org.apache.pekko.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import org.apache.pekko.http.scaladsl.model.*
import org.apache.pekko.http.scaladsl.model.headers.{Host, Origin}
import org.apache.pekko.http.scaladsl.server.*
import org.apache.pekko.http.scaladsl.server.Directives.*
import org.apache.pekko.stream.Materializer
import org.apache.pekko.stream.scaladsl.Sink
import org.apache.pekko.util.ByteString
import org.slf4j.LoggerFactory
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}
import services.FileService
import spray.json.*

private case class Suggestion(mot: String, probabilite: Double)
private case class SuggestionsReponse(suggestions: List[Suggestion])
private case class CompletionReponse(
    prefixe: String,
    suggestions: List[Suggestion]
)
private case class ModelesReponse(models: List[ModelInfo])
private case class TexteReponse(texte: String)
private case class StatutReponse(status: String)
private case class ErreurReponse(error: String)

private object FormatsJson extends SprayJsonSupport with DefaultJsonProtocol:
  given RootJsonFormat[Suggestion] = jsonFormat2(Suggestion.apply)
  given RootJsonFormat[SuggestionsReponse] = jsonFormat1(
    SuggestionsReponse.apply
  )
  given RootJsonFormat[CompletionReponse] = jsonFormat2(CompletionReponse.apply)
  given RootJsonFormat[ModelInfo] = jsonFormat2(ModelInfo.apply)
  given RootJsonFormat[ModelesReponse] = jsonFormat1(ModelesReponse.apply)
  given RootJsonFormat[TexteReponse] = jsonFormat1(TexteReponse.apply)
  given RootJsonFormat[StatutReponse] = jsonFormat1(StatutReponse.apply)
  given RootJsonFormat[ErreurReponse] = jsonFormat1(ErreurReponse.apply)

object Limites:
  val TailleMin = 1
  val TailleMax = models.ModeleNGramme.TailleMax
  val NombreSuggestionsMax = 50
  val NombreMotsMax = 1000
  val OctetsUploadMax: Long = 64L * 1024 * 1024

/** @param blocage
  *   contexte d'exécution dédié à l'entraînement et aux accès disque, pour ne
  *   pas immobiliser les threads qui servent les requêtes
  */
class ApiRoutes(manager: ModelManager, blocage: ExecutionContext)(using
    mat: Materializer,
    ec: ExecutionContext
):
  import FormatsJson.given

  private val log = LoggerFactory.getLogger(getClass)

  private def erreur(code: StatusCode, message: String): Route =
    complete(code -> ErreurReponse(message))

  private def repondreErreur(e: ErreurModele): Route = e match
    case ErreurModele.IdentifiantInvalide(_) =>
      erreur(
        StatusCodes.BadRequest,
        "Nom de modèle invalide : 64 caractères au plus (lettres, chiffres, espaces, - et _)"
      )
    case ErreurModele.Inconnu(id) =>
      erreur(StatusCodes.NotFound, s"Modèle '$id' introuvable")
    case ErreurModele.DejaExistant(id) =>
      erreur(StatusCodes.Conflict, s"Le modèle '$id' existe déjà")
    case ErreurModele.TexteInsuffisant =>
      erreur(
        StatusCodes.BadRequest,
        "Le texte fourni contient trop peu de mots"
      )
    case ErreurModele.Protege(_) =>
      erreur(
        StatusCodes.BadRequest,
        "Le modèle par défaut ne peut pas être supprimé"
      )

  private def avecSuggestionneur(modelId: String, taille: Int)(
      suite: SuggestionNGramme => Route
  ): Route =
    validate(
      taille >= Limites.TailleMin && taille <= Limites.TailleMax,
      s"La taille doit être comprise entre ${Limites.TailleMin} et ${Limites.TailleMax}"
    ) {
      manager.suggestionneur(modelId, taille) match
        case Some(s) => suite(s)
        case None    => repondreErreur(ErreurModele.Inconnu(modelId))
    }

  private def validerNombre(nombre: Int): Directive0 =
    validate(
      nombre >= 1 && nombre <= Limites.NombreSuggestionsMax,
      s"Le nombre de suggestions doit être compris entre 1 et ${Limites.NombreSuggestionsMax}"
    )

  private def versSuggestions(liste: List[(String, Double)]): List[Suggestion] =
    liste.map(Suggestion(_, _))

  private val suggestionsRoute = path("suggestions") {
    get {
      parameters(
        "texte",
        "taille".as[Int].?(Limites.TailleMax),
        "nombre".as[Int].?(5),
        "modelId".?(ModelManager.IdDefaut)
      ) { (texte, taille, nombre, modelId) =>
        validerNombre(nombre) {
          avecSuggestionneur(modelId, taille) { s =>
            complete(
              SuggestionsReponse(versSuggestions(s.suggerer(texte, nombre)))
            )
          }
        }
      }
    }
  }

  private val completionRoute = path("completion") {
    get {
      parameters(
        "texte",
        "taille".as[Int].?(Limites.TailleMax),
        "nombre".as[Int].?(3),
        "modelId".?(ModelManager.IdDefaut)
      ) { (texte, taille, nombre, modelId) =>
        validerNombre(nombre) {
          avecSuggestionneur(modelId, taille) { s =>
            val c = s.completer(texte, nombre)
            complete(
              CompletionReponse(c.prefixe, versSuggestions(c.suggestions))
            )
          }
        }
      }
    }
  }

  private val genererRoute = path("generer") {
    get {
      parameters(
        "prefixe",
        "tailleNGramme".as[Int].?(Limites.TailleMax),
        "nombreMots".as[Int].?(50),
        "modelId".?(ModelManager.IdDefaut)
      ) { (prefixe, taille, nombreMots, modelId) =>
        validate(
          nombreMots >= 1 && nombreMots <= Limites.NombreMotsMax,
          s"Le nombre de mots doit être compris entre 1 et ${Limites.NombreMotsMax}"
        ) {
          avecSuggestionneur(modelId, taille) { s =>
            complete(
              TexteReponse(GenerateurTexte(s).generer(prefixe, nombreMots))
            )
          }
        }
      }
    }
  }

  private val modelesRoute = pathPrefix("models") {
    concat(
      pathEndOrSingleSlash {
        get(complete(ModelesReponse(manager.lister())))
      },
      path(Segment) { id =>
        (delete & memeOrigine) {
          onSuccess(Future(manager.supprimer(id))(using blocage)) {
            case Right(_) =>
              complete(StatutReponse(s"Modèle '$id' supprimé avec succès"))
            case Left(e) => repondreErreur(e)
          }
        }
      }
    )
  }

  private def lireFichiers(
      formulaire: Multipart.FormData
  ): Future[List[(String, Array[Byte])]] =
    formulaire.parts
      .mapAsync(1) { partie =>
        partie.entity.dataBytes
          .runFold(ByteString.empty)(_ ++ _)
          .map(octets => partie.filename.map((_, octets.toArray)))
      }
      .collect { case Some(fichier) => fichier }
      .runWith(Sink.seq)
      .map(_.toList)

  private val uploadRoute = path("upload") {
    (post & memeOrigine) {
      parameters("modelId", "append".as[Boolean].?(false)) {
        (modelId, ajouter) =>
          withSizeLimit(Limites.OctetsUploadMax) {
            entity(as[Multipart.FormData]) { formulaire =>
              val traitement = lireFichiers(formulaire).flatMap { fichiers =>
                val refuses =
                  fichiers.map(_._1).filterNot(FileService.estFichierTexte)
                if refuses.nonEmpty then Future.successful(Left(refuses))
                else
                  Future(
                    Right(
                      manager.entrainer(
                        modelId,
                        fichiers.map((_, octets) =>
                          FileService.preparer(octets)
                        ),
                        ajouter
                      )
                    )
                  )(using blocage)
              }
              onComplete(traitement) {
                case Success(Left(refuses)) =>
                  erreur(
                    StatusCodes.BadRequest,
                    s"Seuls les fichiers .txt sont acceptés : ${refuses.mkString(", ")}"
                  )
                case Success(Right(Left(e)))  => repondreErreur(e)
                case Success(Right(Right(_))) =>
                  complete(StatutReponse("Modèle mis à jour avec succès"))
                case Failure(ex) => failWith(ex)
              }
            }
          }
      }
    }
  }

  /** Sans authentification, le serveur local doit refuser qu'une page d'un
    * autre site modifie ses modèles : un navigateur envoie alors son en-tête
    * Origin.
    */
  private def memeOrigine: Directive0 =
    extractRequest.flatMap { requete =>
      val autorise = requete.header[Origin].forall { origine =>
        val hote = requete.header[Host]
        origine.origins.nonEmpty && origine.origins.forall { o =>
          hote.exists(h =>
            o.host.host.address.equalsIgnoreCase(h.host.address) &&
              o.host.port == h.port
          )
        }
      }
      if autorise then pass
      else
        complete(
          StatusCodes.Forbidden -> ErreurReponse(
            "Requête d'une autre origine refusée"
          )
        )
    }

  private val gestionExceptions = ExceptionHandler {
    case e: EntityStreamSizeException =>
      log.warn("Upload refusé : {}", e.getMessage)
      erreur(
        StatusCodes.ContentTooLarge,
        "Les fichiers envoyés sont trop volumineux"
      )
    case e: Exception =>
      log.error("Erreur lors du traitement de la requête", e)
      erreur(StatusCodes.InternalServerError, "Erreur interne du serveur")
  }

  /** Toutes les erreurs de l'API sont rendues en JSON. */
  private val gestionRejets = RejectionHandler
    .newBuilder()
    .handle { case MissingQueryParamRejection(nom) =>
      erreur(StatusCodes.BadRequest, s"Paramètre manquant : $nom")
    }
    .result()
    .withFallback(RejectionHandler.default)
    .mapRejectionResponse {
      case reponse @ HttpResponse(_, _, entite: HttpEntity.Strict, _)
          if entite.contentType != ContentTypes.`application/json` =>
        val corps = ErreurReponse(entite.data.utf8String).toJson.compactPrint
        reponse.withEntity(ContentTypes.`application/json`, corps)
      case autre => autre
    }

  val routes: Route = pathPrefix("api") {
    handleExceptions(gestionExceptions) {
      handleRejections(gestionRejets) {
        concat(
          suggestionsRoute,
          completionRoute,
          genererRoute,
          modelesRoute,
          uploadRoute
        )
      }
    }
  }

class StaticRoutes:
  val routes: Route = concat(
    pathPrefix("static")(getFromResourceDirectory("static")),
    pathPrefix("app") {
      concat(
        path("suggestions")(getFromResource("static/suggestions.html")),
        path("generator")(getFromResource("static/generator.html")),
        path("settings")(getFromResource("static/settings.html")),
        pathEndOrSingleSlash(getFromResource("static/index.html"))
      )
    },
    pathEndOrSingleSlash(redirect("/app", StatusCodes.PermanentRedirect))
  )

object Routes:
  def apply(manager: ModelManager, blocage: ExecutionContext)(using
      Materializer,
      ExecutionContext
  ): Route =
    concat(ApiRoutes(manager, blocage).routes, StaticRoutes().routes)
