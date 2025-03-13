import models.*
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class TestsSuggestionNGramme extends AnyFlatSpec with Matchers:
  private val texte =
    "le chat noir mange le poisson le chat noir dort le chien noir court et le quartier dort"
  private val modeles = ModeleNGramme.construire(ModeleNGramme.compter(texte))

  "SuggestionNGramme" should "extraire les derniers mots comme contexte" in {
    val s = SuggestionNGramme(Trie[String](), 2)
    s.extraireContexte("Le chat, noir !") shouldBe "chat noir"
    s.extraireContexte("chat") shouldBe "chat"
    s.extraireContexte("") shouldBe ""
    s.extraireContexte(null) shouldBe ""
  }

  it should "suggérer selon le contexte exact" in {
    val trie = Trie[String]().inserer(
      "chat noir",
      Probabilites(Map("mange" -> 0.7, "dort" -> 0.3))
    )
    SuggestionNGramme(trie, 2).suggerer("le chat noir") shouldBe
      List(("mange", 0.7), ("dort", 0.3))
  }

  it should "ne rien suggérer sans contexte connu" in {
    SuggestionNGramme(Trie[String](), 3)
      .suggerer("contexte inconnu") shouldBe Nil
    modeles(3).suggerer("xyz abc def") shouldBe Nil
  }

  it should "se replier sur un contexte plus court si la phrase est courte" in {
    modeles(3).suggerer("chat").map(_._1) should contain("noir")
  }

  it should "se replier sur un contexte plus court si le contexte est inconnu" in {
    // "xyz le chat" n'existe pas, mais "le chat" oui.
    modeles(3).suggerer("xyz le chat").headOption.map(_._1) shouldBe Some(
      "noir"
    )
  }

  it should "classer le contexte long avant les contextes courts" in {
    // Après "le chat noir" : mange et dort. "court" n'apparaît qu'après "noir".
    val mots = modeles(3).suggerer("le chat noir", 10).map(_._1)
    mots.take(2).toSet shouldBe Set("mange", "dort")
    mots should contain("court")
  }

  it should "renvoyer des probabilités dont la somme vaut 1" in {
    modeles(3).suggerer("le chat noir", 50).map(_._2).sum shouldBe 1.0 +- 1e-9
  }

  it should "reproduire l'exemple de repli du README" in {
    val m = ModeleNGramme.construire(
      ModeleNGramme.compter("the cat sleeps a cat eats")
    )
    val List((premier, p1), (second, p2)) =
      m(2).suggerer("the cat", 5): @unchecked
    premier shouldBe "sleeps"
    second shouldBe "eats"
    p1 shouldBe (1.0 / 1.2) +- 1e-9
    p2 shouldBe (0.2 / 1.2) +- 1e-9
  }

  "completer" should "prédire le mot suivant après un espace" in {
    val c = modeles(3).completer("le chat ")
    c.prefixe shouldBe ""
    c.suggestions.headOption.map(_._1) shouldBe Some("noir")
  }

  it should "finir un mot commencé selon le contexte" in {
    val c = modeles(3).completer("le chat no")
    c.prefixe shouldBe "no"
    c.suggestions.headOption.map(_._1) shouldBe Some("noir")
  }

  it should "se rabattre sur le vocabulaire si le contexte n'aide pas" in {
    val c = modeles(3).completer("xyz qua")
    c.suggestions.map(_._1) shouldBe List("quartier")
  }

  it should "ne pas proposer le mot déjà entièrement tapé" in {
    modeles(3)
      .completer("le chat")
      .suggestions
      .map(_._1) should not contain "chat"
  }

  it should "ne rien proposer pour un texte vide" in {
    modeles(3).completer("") shouldBe Completion("", Nil)
  }
