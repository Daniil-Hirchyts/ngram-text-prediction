import api.{Limites, Routes}
import java.nio.file.Files
import models.*
import org.apache.pekko.http.scaladsl.model.*
import org.apache.pekko.http.scaladsl.model.headers.{Host, HttpOrigin, Origin}
import org.apache.pekko.http.scaladsl.testkit.{
  RouteTestTimeout,
  ScalatestRouteTest
}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import scala.concurrent.duration.*
import spray.json.*

class TestsRoutes extends AnyFlatSpec with Matchers with ScalatestRouteTest:
  given RouteTestTimeout = RouteTestTimeout(10.seconds)

  private val racine = Files.createTempDirectory("routes-test")
  private val manager = ModelManager(DepotModeles(racine.resolve("modeles.db")))
  manager.initialiser()
  manager.entrainer(
    "essai",
    List("le thé était très bon le thé était chaud"),
    false
  )

  private val routes = Routes(manager, executor)

  private def json = responseAs[String].parseJson.asJsObject
  private def mots =
    json.fields("suggestions") match
      case JsArray(l) =>
        l.map(_.asJsObject.fields("mot"))
          .collect { case JsString(m) => m }
          .toList
      case _ => Nil

  private def formulaire(fichiers: (String, String)*) =
    Multipart
      .FormData(fichiers.map { (nom, contenu) =>
        Multipart.FormData.BodyPart.Strict(
          "files",
          HttpEntity(ContentTypes.`text/plain(UTF-8)`, contenu),
          Map("filename" -> nom)
        )
      }*)
      .toEntity

  "GET /api/suggestions" should "suggérer des mots avec leurs accents" in {
    Get(
      "/api/suggestions?texte=le%20th%C3%A9&modelId=essai"
    ) ~> routes ~> check {
      status shouldBe StatusCodes.OK
      mots shouldBe List("était")
    }
  }

  it should "se replier sur un contexte plus court" in {
    Get(
      "/api/suggestions?texte=th%C3%A9&taille=3&modelId=essai"
    ) ~> routes ~> check {
      mots shouldBe List("était")
    }
  }

  it should "répondre 400 à un nombre ou une taille hors limites" in {
    for parametres <- List(
        "nombre=0",
        "nombre=-1",
        "nombre=51",
        "taille=0",
        "taille=4"
      )
    do
      Get(s"/api/suggestions?texte=x&$parametres") ~> routes ~> check {
        status shouldBe StatusCodes.BadRequest
        json.fields should contain key "error"
      }
  }

  it should "répondre 400 en JSON à un paramètre manquant ou mal formé" in {
    Get("/api/suggestions?texte=x&nombre=abc") ~> routes ~> check {
      status shouldBe StatusCodes.BadRequest
      json.fields should contain key "error"
    }
  }

  it should "répondre 400 à un paramètre obligatoire absent" in {
    Get("/api/suggestions") ~> routes ~> check {
      status shouldBe StatusCodes.BadRequest
      json.fields("error") shouldBe JsString("Paramètre manquant : texte")
    }
  }

  it should "répondre 404 à un modèle inconnu au lieu d'utiliser le modèle par défaut" in {
    Get("/api/suggestions?texte=alice&modelId=absent") ~> routes ~> check {
      status shouldBe StatusCodes.NotFound
    }
  }

  "GET /api/completion" should "indiquer le préfixe et finir le mot" in {
    Get("/api/completion?texte=le%20th&modelId=essai") ~> routes ~> check {
      json.fields("prefixe") shouldBe JsString("th")
      mots.headOption shouldBe Some("thé")
    }
  }

  it should "prédire le mot suivant après un espace" in {
    Get(
      "/api/completion?texte=le%20th%C3%A9%20&modelId=essai"
    ) ~> routes ~> check {
      json.fields("prefixe") shouldBe JsString("")
      mots.headOption shouldBe Some("était")
    }
  }

  "GET /api/generer" should "prolonger le préfixe" in {
    Get(
      "/api/generer?prefixe=le%20th%C3%A9&nombreMots=1&modelId=essai"
    ) ~> routes ~> check {
      json.fields("texte") shouldBe JsString("le thé était")
    }
  }

  it should "refuser une génération trop longue" in {
    Get(
      s"/api/generer?prefixe=x&nombreMots=${Limites.NombreMotsMax + 1}"
    ) ~> routes ~> check {
      status shouldBe StatusCodes.BadRequest
    }
  }

  "POST /api/upload" should "créer puis enrichir un modèle" in {
    Post(
      "/api/upload?modelId=neuf",
      formulaire("a.txt" -> "red fox runs")
    ) ~> routes ~> check {
      status shouldBe StatusCodes.OK
    }
    Post(
      "/api/upload?modelId=neuf&append=true",
      formulaire("b.txt" -> "blue cat sleeps")
    ) ~> routes ~> check {
      status shouldBe StatusCodes.OK
    }
    Get("/api/suggestions?texte=red&taille=1&modelId=neuf") ~> routes ~> check {
      mots shouldBe List("fox")
    }
    Get(
      "/api/suggestions?texte=blue&taille=1&modelId=neuf"
    ) ~> routes ~> check {
      mots shouldBe List("cat")
    }
  }

  it should "répondre 409 si le modèle à créer existe déjà" in {
    Post(
      "/api/upload?modelId=essai",
      formulaire("a.txt" -> "red fox runs")
    ) ~> routes ~> check {
      status shouldBe StatusCodes.Conflict
    }
  }

  it should "répondre 404 si le modèle à enrichir n'existe pas" in {
    Post(
      "/api/upload?modelId=absent&append=true",
      formulaire("a.txt" -> "red fox runs")
    ) ~> routes ~> check {
      status shouldBe StatusCodes.NotFound
    }
  }

  it should "refuser un nom de modèle sortant du dossier" in {
    Post(
      "/api/upload?modelId=..%2Fpirate",
      formulaire("a.txt" -> "red fox runs")
    ) ~> routes ~> check {
      status shouldBe StatusCodes.BadRequest
    }
  }

  it should "refuser les fichiers qui ne sont pas des .txt en les nommant" in {
    Post(
      "/api/upload?modelId=autre",
      formulaire("a.pdf" -> "red fox runs")
    ) ~> routes ~> check {
      status shouldBe StatusCodes.BadRequest
      json.fields("error").toString should include("a.pdf")
    }
  }

  it should "refuser une requête venant d'un autre site" in {
    Post("/api/upload?modelId=intrus", formulaire("a.txt" -> "red fox runs"))
      .withHeaders(
        Host("localhost", 8080),
        Origin(HttpOrigin("https://evil.example"))
      ) ~> routes ~> check {
      status shouldBe StatusCodes.Forbidden
    }
    manager.lister().map(_.id) should not contain "intrus"
  }

  it should "accepter une requête venant de l'application elle-même" in {
    Post("/api/upload?modelId=local", formulaire("a.txt" -> "red fox runs"))
      .withHeaders(
        Host("localhost", 8080),
        Origin(HttpOrigin("http://localhost:8080"))
      ) ~> routes ~> check {
      status shouldBe StatusCodes.OK
    }
  }

  "DELETE /api/models" should "supprimer un modèle puis répondre 404" in {
    manager.entrainer("jetable", List("red fox runs"), false)
    Delete("/api/models/jetable") ~> routes ~> check {
      status shouldBe StatusCodes.OK
    }
    Delete("/api/models/jetable") ~> routes ~> check {
      status shouldBe StatusCodes.NotFound
    }
  }

  it should "protéger le modèle par défaut" in {
    Delete("/api/models/default") ~> routes ~> check {
      status shouldBe StatusCodes.BadRequest
    }
  }

  it should "ne jamais toucher un fichier hors de la base" in {
    val victime = racine.resolve("secret.dat")
    Files.writeString(victime, "garder")
    Delete("/api/models/..%2Fsecret") ~> routes ~> check {
      status shouldBe StatusCodes.NotFound
    }
    Files.exists(victime) shouldBe true
  }

  it should "refuser une suppression venant d'un autre site" in {
    Delete("/api/models/essai")
      .withHeaders(
        Host("localhost", 8080),
        Origin(HttpOrigin("https://evil.example"))
      ) ~> routes ~> check {
      status shouldBe StatusCodes.Forbidden
    }
  }

  "Une route d'API inconnue" should "répondre 404 en JSON" in {
    Get("/api/inexistante") ~> routes ~> check {
      status shouldBe StatusCodes.NotFound
      json.fields should contain key "error"
    }
  }

  "Les pages" should "être servies" in {
    Get("/app") ~> routes ~> check { status shouldBe StatusCodes.OK }
    Get("/app/settings") ~> routes ~> check { status shouldBe StatusCodes.OK }
    Get("/static/js/shared.js") ~> routes ~> check {
      status shouldBe StatusCodes.OK
    }
  }
