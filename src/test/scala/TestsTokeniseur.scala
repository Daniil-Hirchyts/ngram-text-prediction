import models.*
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class TestsTokeniseur extends AnyFlatSpec with Matchers:
  "Le tokeniseur" should "conserver les accents" in {
    Tokeniseur.tokeniser("Le thé était à côté.") shouldBe
      List("le", "thé", "était", "à", "côté")
  }

  it should "garder apostrophes et traits d'union à l'intérieur des mots" in {
    Tokeniseur.tokeniser("C’est l'arc-en-ciel, don't panic - ok") shouldBe
      List("c'est", "l'arc-en-ciel", "don't", "panic", "ok")
  }

  it should "unifier les formes composées et décomposées d'un même caractère" in {
    Tokeniseur.tokeniser("été") shouldBe List("été")
  }

  it should "ignorer chiffres et ponctuation" in {
    Tokeniseur.tokeniser("Chapitre 12 : _fin_ !") shouldBe List(
      "chapitre",
      "fin"
    )
    Tokeniseur.tokeniser(null) shouldBe Nil
  }

  it should "distinguer le mot en cours de frappe des mots terminés" in {
    Tokeniseur.separerMotPartiel("the que") shouldBe (List("the"), "que")
    Tokeniseur.separerMotPartiel("the que ") shouldBe (List("the", "que"), "")
    Tokeniseur.separerMotPartiel("the queen,") shouldBe (
      List("the", "queen"),
      ""
    )
    Tokeniseur.separerMotPartiel("") shouldBe (Nil, "")
  }
