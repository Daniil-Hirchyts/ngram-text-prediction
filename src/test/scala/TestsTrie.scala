import models.*
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class TestsTrie extends AnyFlatSpec with Matchers:
  private val probs = Probabilites(Map("monde" -> 0.8, "tous" -> 0.2))

  "Un Trie" should "retrouver une valeur insérée, sans tenir compte de la casse" in {
    val trie = Trie[String]().inserer("Bonjour", probs)
    trie.trouver("bonjour") shouldBe Some(probs)
    trie.trouver("BONJOUR") shouldBe Some(probs)
  }

  it should "ne rien trouver pour une clé absente ou un simple préfixe" in {
    val trie = Trie[String]().inserer("bonjour", probs)
    trie.trouver("inexistant") shouldBe None
    trie.trouver("bon") shouldBe None
    trie.trouver("") shouldBe None
    trie.trouver(null) shouldBe None
  }

  it should "refuser les insertions invalides" in {
    val trie = Trie[String]()
    an[IllegalArgumentException] should be thrownBy trie.inserer(null, probs)
    an[IllegalArgumentException] should be thrownBy trie.inserer("", probs)
    an[IllegalArgumentException] should be thrownBy trie.inserer("mot", null)
  }

  it should "rester immuable" in {
    val vide = Trie[String]()
    val rempli = vide.inserer("animal", probs)
    vide.trouver("animal") shouldBe None
    rempli.trouver("animal") shouldBe Some(probs)
  }

  it should "lister les clés commençant par un préfixe" in {
    val trie = Trie[String]()
      .inserer("que", probs)
      .inserer("queen", probs)
      .inserer("quiet", probs)
      .inserer("rabbit", probs)
    trie.clesAvecPrefixe("que").map(_._1).toSet shouldBe Set("que", "queen")
    trie.clesAvecPrefixe("zzz") shouldBe Nil
    trie.clesAvecPrefixe(null) shouldBe Nil
  }

  it should "renvoyer les éléments les plus probables d'une clé" in {
    val trie = Trie[String]().inserer("bonjour", probs)
    trie.obtenirPlusProbables("bonjour", 1) shouldBe List(("monde", 0.8))
    trie.obtenirPlusProbables("inexistant") shouldBe Nil
  }
