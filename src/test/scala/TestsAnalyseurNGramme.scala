import models.*
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class TestsAnalyseurNGramme extends AnyFlatSpec with Matchers:
  "AnalyseurTexte" should "compter les mots suivant chaque bigramme" in {
    val comptages = AnalyseurTexte(2).compterNGrammes(
      "le chat noir mange le poisson le chat noir dort"
    )
    comptages(List("le", "chat")) shouldBe Map("noir" -> 2)
    comptages(List("chat", "noir")) shouldBe Map("mange" -> 1, "dort" -> 1)
    comptages(List("poisson", "le")) shouldBe Map("chat" -> 1)
  }

  it should "compter les mots suivant chaque trigramme" in {
    val comptages = AnalyseurTexte(3).compterNGrammes(
      "le chat noir mange le poisson le chat noir dort bien"
    )
    comptages(List("le", "chat", "noir")) shouldBe Map(
      "mange" -> 1,
      "dort" -> 1
    )
    // Dernier groupe du texte : aucun mot ne le suit.
    comptages.contains(List("noir", "dort", "bien")) shouldBe false
  }

  it should "calculer les probabilités à partir des comptages" in {
    val probabilites = AnalyseurTexte(2).calculerProbabilites(
      Map(List("le", "chat") -> Map("noir" -> 2, "blanc" -> 1))
    )
    probabilites(List("le", "chat")).donnees("noir") shouldBe 2.0 / 3.0
    probabilites(List("le", "chat")).donnees("blanc") shouldBe 1.0 / 3.0
  }

  it should "convertir les n-grammes en clés pour le trie" in {
    val probs = Probabilites(Map("suivant" -> 1.0))
    AnalyseurTexte(3).convertirNGrammesEnCles(
      Map(List("le", "chat", "noir") -> probs)
    ) shouldBe Map("le chat noir" -> probs)
  }

  it should "gérer un texte vide, null ou trop court" in {
    val analyseur = AnalyseurTexte(3)
    analyseur.compterNGrammes("") shouldBe Map.empty
    analyseur.compterNGrammes(null) shouldBe Map.empty
    analyseur.compterNGrammes("trois mots seulement") shouldBe Map.empty
  }

  it should "refuser une taille invalide" in {
    an[IllegalArgumentException] should be thrownBy AnalyseurTexte(0)
  }
