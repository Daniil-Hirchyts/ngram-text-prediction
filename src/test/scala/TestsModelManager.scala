import java.nio.file.{Files, Path}
import models.*
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class TestsModelManager extends AnyFlatSpec with Matchers:
  private def base(): Path =
    Files.createTempDirectory("modeles-test").resolve("modeles.db")

  private def manager(fichier: Path = base()): ModelManager =
    val m = ModelManager(DepotModeles(fichier))
    m.initialiser()
    m

  private def mots(m: ModelManager, id: String, texte: String) =
    m.suggestionneur(id, 1).get.suggerer(texte).map(_._1)

  "ModelManager" should "créer le modèle par défaut sur une base vide" in {
    manager().lister().map(_.id) shouldBe List("default")
  }

  it should "conserver les modèles après un redémarrage" in {
    val fichier = base()
    manager(fichier).entrainer(
      "essai",
      List("red fox runs fast"),
      false
    ) shouldBe Right(())
    val redemarre = manager(fichier)
    redemarre.lister().map(_.id) should contain("essai")
    mots(redemarre, "essai", "red") shouldBe List("fox")
  }

  it should "ne pas recréer un modèle supprimé après un redémarrage" in {
    val fichier = base()
    val m = manager(fichier)
    m.entrainer("essai", List("red fox runs fast"), false)
    m.supprimer("essai") shouldBe Right(())
    manager(fichier).lister().map(_.id) should not contain "essai"
  }

  it should "enrichir un modèle sans perdre ce qu'il avait appris" in {
    val m = manager()
    m.entrainer("essai", List("red fox runs"), false)
    m.entrainer("essai", List("blue cat sleeps"), true) shouldBe Right(())
    mots(m, "essai", "red") shouldBe List("fox")
    mots(m, "essai", "blue") shouldBe List("cat")
  }

  it should "additionner les comptages lors d'un enrichissement" in {
    val m = manager()
    m.entrainer("essai", List("red fox"), false)
    m.entrainer("essai", List("red cat red cat"), true)
    m.suggestionneur("essai", 1).get.suggerer("red").head shouldBe
      ("cat", 2.0 / 3.0)
  }

  it should "refuser d'écraser un modèle existant" in {
    val m = manager()
    m.entrainer("essai", List("red fox runs"), false)
    m.entrainer("essai", List("blue cat sleeps"), false) shouldBe
      Left(ErreurModele.DejaExistant("essai"))
    mots(m, "essai", "red") shouldBe List("fox")
  }

  it should "refuser d'enrichir un modèle inconnu" in {
    manager().entrainer("absent", List("red fox"), true) shouldBe
      Left(ErreurModele.Inconnu("absent"))
  }

  it should "refuser les identifiants dangereux" in {
    val m = manager()
    for id <- List("../secret", "a/b", "", " ", ".", "x" * 65) do
      m.entrainer(id, List("red fox runs"), false) shouldBe
        Left(ErreurModele.IdentifiantInvalide(id))
  }

  it should "accepter les noms avec accents et espaces" in {
    manager().entrainer(
      "mon modèle-2",
      List("red fox runs"),
      false
    ) shouldBe Right(())
  }

  it should "refuser un texte trop court" in {
    manager().entrainer("essai", List("mot"), false) shouldBe
      Left(ErreurModele.TexteInsuffisant)
  }

  it should "protéger le modèle par défaut et signaler un modèle inconnu" in {
    val m = manager()
    m.supprimer("default") shouldBe Left(ErreurModele.Protege("default"))
    m.supprimer("absent") shouldBe Left(ErreurModele.Inconnu("absent"))
  }

  it should "laisser le modèle intact si l'enregistrement échoue" in {
    val fichier = base()
    val depot = DepotModeles(fichier)
    depot.ajouter("essai", "Essai", ModeleNGramme.compter("red fox runs"))
    val invalide: ModeleNGramme.Comptages =
      Map(
        1 -> Map("red" -> Map("cat" -> 1)),
        2 -> Map[String, Map[String, Int]]((null: String) -> Map("x" -> 1))
      )
    an[Exception] should be thrownBy depot.ajouter("essai", "Essai", invalide)
    depot.charger("essai")(1)("red") shouldBe Map("fox" -> 1)
  }

  it should "apprendre les corpus initiaux depuis un dossier" in {
    val racine = Files.createTempDirectory("corpus-test")
    Files.createDirectories(racine.resolve("en"))
    Files.createDirectories(racine.resolve("fr"))
    Files.writeString(racine.resolve("en/a.txt"), "the red fox runs")
    Files.writeString(racine.resolve("fr/a.txt"), "le thé était bon")
    val m = ModelManager(DepotModeles(base()), Some(racine))
    m.initialiser()
    m.lister().map(_.id) shouldBe List("default", "francais")
    mots(m, "francais", "thé") shouldBe List("était")
  }
