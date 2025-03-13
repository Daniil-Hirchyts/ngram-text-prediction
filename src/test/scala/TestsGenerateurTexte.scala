import models.*
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import scala.util.Random

class TestsGenerateurTexte extends AnyFlatSpec with Matchers:
  private def suggestionneur(entrees: (String, Map[String, Double])*) =
    val trie = entrees.foldLeft(Trie[String]()) { case (t, (cle, probs)) =>
      t.inserer(cle, Probabilites(probs))
    }
    SuggestionNGramme(trie, 1)

  "GenerateurTexte" should "prolonger le préfixe du nombre de mots demandé" in {
    val g = GenerateurTexte(
      suggestionneur("début" -> Map("mot" -> 1.0), "mot" -> Map("mot" -> 1.0))
    )
    g.generer("début", 3) shouldBe "début mot mot mot"
  }

  it should "s'arrêter quand aucun mot ne peut suivre" in {
    val g = GenerateurTexte(suggestionneur("début" -> Map("fin" -> 1.0)))
    g.generer("début", 10) shouldBe "début fin"
  }

  it should "gérer les entrées nulles, vides ou négatives" in {
    val g = GenerateurTexte(suggestionneur("début" -> Map("mot" -> 1.0)))
    g.generer(null, 5) shouldBe ""
    g.generer("  ", 5) shouldBe ""
    g.generer("début", 0) shouldBe "début"
    g.generer("début", -1) shouldBe "début"
  }

  it should "pouvoir tirer des mots au-delà des trois plus probables" in {
    val mots = (1 to 6).map(i => s"m$i" -> 1.0 / 6).toMap
    val g = GenerateurTexte(suggestionneur("début" -> mots), new Random(1))
    val tires = (1 to 300).map(_ => g.generer("début", 1).split(" ").last).toSet
    tires shouldBe mots.keySet
  }

  it should "respecter les probabilités lors du tirage" in {
    val g = GenerateurTexte(
      suggestionneur("début" -> Map("rare" -> 0.1, "courant" -> 0.9)),
      new Random(7)
    )
    val tirages = (1 to 2000).map(_ => g.generer("début", 1).split(" ").last)
    (tirages.count(_ == "courant") / 2000.0) shouldBe 0.9 +- 0.03
  }

  it should "continuer avec le contexte le plus court disponible" in {
    val modeles = ModeleNGramme.construire(
      ModeleNGramme.compter("le chat dort le chat dort le chat dort")
    )
    GenerateurTexte(modeles(3)).generer("chat", 2) shouldBe "chat dort le"
  }

  it should "générer un long texte en temps raisonnable" in {
    val g = GenerateurTexte(suggestionneur("mot" -> Map("mot" -> 1.0)))
    val debut = System.nanoTime()
    g.generer("mot", 100000).split(" ").length shouldBe 100001
    ((System.nanoTime() - debut) / 1e9) should be < 5.0
  }
