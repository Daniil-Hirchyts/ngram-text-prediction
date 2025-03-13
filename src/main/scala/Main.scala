import api.Routes
import java.nio.file.Paths
import models.{DepotModeles, ModelManager}
import org.apache.pekko.actor.typed.{ActorSystem, DispatcherSelector}
import org.apache.pekko.actor.typed.scaladsl.Behaviors
import org.apache.pekko.http.scaladsl.Http
import org.apache.pekko.stream.Materializer
import org.slf4j.LoggerFactory
import scala.concurrent.ExecutionContext
import scala.concurrent.duration.*
import scala.util.{Failure, Success}

/** Lance le serveur. Il s'arrête proprement à la réception de SIGTERM ou
  * Ctrl-C, et fonctionne donc aussi sans terminal (Docker, service, nohup).
  */
@main def main(): Unit =
  val log = LoggerFactory.getLogger("main")
  def variable(nom: String, defaut: String) = sys.env.getOrElse(nom, defaut)

  val hote = variable("HOST", "localhost")
  val port = variable("PORT", "8080").toInt
  val base = Paths.get(variable("MODELS_DIR", "models")).resolve("modeles.db")
  val corpus = Paths.get(variable("TRAINING_TEXTS_DIR", "training-texts"))

  val manager = ModelManager(DepotModeles(base), Some(corpus))
  manager.initialiser()

  given system: ActorSystem[Nothing] =
    ActorSystem(Behaviors.empty, "predictionTexteSysteme")
  given ec: ExecutionContext = system.executionContext
  given Materializer = Materializer(system)
  val blocage = system.dispatchers.lookup(DispatcherSelector.blocking())

  Http().newServerAt(hote, port).bind(Routes(manager, blocage)).onComplete {
    case Success(liaison) =>
      liaison.addToCoordinatedShutdown(10.seconds)
      log.info("Serveur démarré sur http://{}:{}/", hote, port)
    case Failure(e) =>
      log.error(s"Impossible d'écouter sur $hote:$port", e)
      system.terminate()
      sys.exit(1)
  }
