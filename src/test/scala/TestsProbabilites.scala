import models.*
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class TestsProbabilites extends AnyFlatSpec with Matchers:
  "Probabilites" should "retourner les éléments les plus probables" in {
    Probabilites(Map("chien" -> 0.7, "chat" -> 0.3, "oiseau" -> 0.2))
      .plusProbables(2) shouldBe List(("chien", 0.7), ("chat", 0.3))
  }

  it should "départager les égalités de façon stable" in {
    Probabilites(Map("b" -> 0.5, "c" -> 0.5, "a" -> 0.5))
      .plusProbables(3)
      .map(_._1) shouldBe List("a", "b", "c")
  }

  it should "refuser un n non positif" in {
    an[IllegalArgumentException] should be thrownBy
      Probabilites(Map("test" -> 1.0)).plusProbables(0)
  }

  it should "normaliser des scores en distribution" in {
    Probabilites.normalisees(Map("a" -> 1.0, "b" -> 3.0)).donnees shouldBe
      Map("a" -> 0.25, "b" -> 0.75)
    Probabilites.normalisees(Map.empty[String, Double]).donnees shouldBe empty
  }
