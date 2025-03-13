import java.nio.file.Paths
import models.*
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import services.FileService

/** Garde-fou de qualité sur les vrais corpus, avec des textes non appris. */
class TestsEvaluation extends AnyFlatSpec with Matchers:
  for langue <- List("en", "fr") do
    s"La prédiction en '$langue'" should "profiter du repli et dépasser un seuil de qualité" in {
      val textes = FileService.lireDossier(Paths.get("training-texts", langue))
      val (entrainement, test) = Evaluation.separer(textes)
      val (exact, avecRepli) = Evaluation.modeles(entrainement)(3)

      val e = Evaluation.evaluer(exact, test)
      val r = Evaluation.evaluer(avecRepli, test)

      r.couverture should be > 0.9
      r.couverture should be > e.couverture
      r.top3 should be > e.top3
      r.top3 should be > 0.15
    }
