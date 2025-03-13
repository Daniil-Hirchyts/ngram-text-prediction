import java.nio.file.Paths
import models.Evaluation
import services.FileService

/** Affiche la qualité de prédiction : sbt "runMain evaluerModeles" */
@main def evaluerModeles(): Unit =
  for langue <- List("en", "fr") do
    val textes = FileService.lireDossier(Paths.get("training-texts", langue))
    val (entrainement, test) = Evaluation.separer(textes)
    println(
      s"\n[$langue] ${entrainement.length} mots d'apprentissage, ${test.length} mots de test"
    )
    Evaluation.modeles(entrainement).toList.sortBy(_._1).foreach {
      case (taille, (exact, avecRepli)) =>
        val e = Evaluation.evaluer(exact, test)
        val r = Evaluation.evaluer(avecRepli, test)
        println(
          f"contexte $taille : exact top-3 ${e.top3 * 100}%5.1f%% couverture ${e.couverture * 100}%5.1f%%" +
            f" | repli top-3 ${r.top3 * 100}%5.1f%% couverture ${r.couverture * 100}%5.1f%%"
        )
    }
